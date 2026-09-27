const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { defineString } = require("firebase-functions/params");
const { initializeApp } = require("firebase-admin/app");
const { getAuth } = require("firebase-admin/auth");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");

initializeApp();

const ADMIN_EMAIL = defineString("ADMIN_EMAIL");

function requireAuth(request) {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Anmeldung erforderlich.");
  }
  return request.auth;
}

function requireModerator(request) {
  const auth = requireAuth(request);
  const role = auth.token.role;
  if (role !== "moderator" && role !== "admin") {
    throw new HttpsError("permission-denied", "Moderatorrechte erforderlich.");
  }
  return auth;
}

function requireAdmin(request) {
  const auth = requireAuth(request);
  if (auth.token.role !== "admin") {
    throw new HttpsError("permission-denied", "Adminrechte erforderlich.");
  }
  return auth;
}

exports.bootstrapAdmin = onCall(async (request) => {
  const auth = requireAuth(request);
  const email = String(auth.token.email || "").trim().toLowerCase();
  const configured = ADMIN_EMAIL.value().trim().toLowerCase();

  if (!configured || email !== configured) {
    throw new HttpsError("permission-denied", "Dieses Konto ist nicht als Admin konfiguriert.");
  }

  const user = await getAuth().getUser(auth.uid);
  await getAuth().setCustomUserClaims(user.uid, {
    ...(user.customClaims || {}),
    role: "admin",
  });

  return { ok: true };
});

exports.setModerator = onCall(async (request) => {
  requireAdmin(request);

  const email = String(request.data?.email || "").trim().toLowerCase();
  const enabled = request.data?.enabled === true;
  if (!email) {
    throw new HttpsError("invalid-argument", "E-Mail fehlt.");
  }

  const user = await getAuth().getUserByEmail(email);
  if (user.customClaims?.role === "admin") {
    throw new HttpsError("failed-precondition", "Ein Admin kann hier nicht herabgestuft werden.");
  }

  await getAuth().setCustomUserClaims(user.uid, {
    ...(user.customClaims || {}),
    role: enabled ? "moderator" : "user",
  });

  return {
    ok: true,
    uid: user.uid,
    email: user.email,
    role: enabled ? "moderator" : "user",
  };
});

exports.approveSubmission = onCall(async (request) => {
  const moderator = requireModerator(request);
  const submissionId = String(request.data?.submissionId || "").trim();
  if (!submissionId) {
    throw new HttpsError("invalid-argument", "submissionId fehlt.");
  }

  const db = getFirestore();
  const submissionRef = db.collection("profile_submissions").doc(submissionId);

  await db.runTransaction(async (tx) => {
    const snapshot = await tx.get(submissionRef);
    if (!snapshot.exists) {
      throw new HttpsError("not-found", "Einreichung nicht gefunden.");
    }

    const submission = snapshot.data();
    if (submission.status !== "pending") {
      throw new HttpsError("failed-precondition", "Die Einreichung ist nicht mehr offen.");
    }

    const targetProfileId = String(submission.targetProfileId || "").trim();
    if (!targetProfileId) {
      throw new HttpsError("failed-precondition", "Zielprofil fehlt.");
    }

    const publishedRef = db.collection("published_profiles").doc(targetProfileId);
    const now = FieldValue.serverTimestamp();

    tx.set(publishedRef, {
      ownerUid: submission.ownerUid,
      creatorName: submission.creatorName || "",
      description: submission.description || "",
      location: submission.location || "",
      tags: Array.isArray(submission.tags) ? submission.tags : [],
      formatVersion: submission.formatVersion || 1,
      profile: submission.profile,
      updatedAt: now,
      approvedBy: moderator.uid,
      sourceSubmissionId: submissionId,
    }, { merge: true });

    tx.update(submissionRef, {
      status: "approved",
      reviewedAt: now,
      reviewedBy: moderator.uid,
      moderatorNote: "",
    });
  });

  return { ok: true };
});

exports.rejectSubmission = onCall(async (request) => {
  const moderator = requireModerator(request);
  const submissionId = String(request.data?.submissionId || "").trim();
  const note = String(request.data?.note || "").trim().slice(0, 500);

  if (!submissionId) {
    throw new HttpsError("invalid-argument", "submissionId fehlt.");
  }

  const ref = getFirestore().collection("profile_submissions").doc(submissionId);
  const snapshot = await ref.get();
  if (!snapshot.exists) {
    throw new HttpsError("not-found", "Einreichung nicht gefunden.");
  }
  if (snapshot.data().status !== "pending") {
    throw new HttpsError("failed-precondition", "Die Einreichung ist nicht mehr offen.");
  }

  await ref.update({
    status: "rejected",
    reviewedAt: FieldValue.serverTimestamp(),
    reviewedBy: moderator.uid,
    moderatorNote: note,
  });

  return { ok: true };
});
