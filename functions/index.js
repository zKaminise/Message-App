// functions/index.js
const functions = require("firebase-functions/v2");
const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const admin = require("firebase-admin");

// Deixe a região explícita. Para Firestore (nam5), "us-central1" é a escolha recomendada.
functions.setGlobalOptions({ region: "us-central1", memory: "256MiB", maxInstances: 5 });

admin.initializeApp();

/**
 * Push em nova mensagem: chats/{chatId}/messages/{messageId}
 * - Notifica todos os membros, exceto o remetente
 * - Inclui data.chatId para deep link no app
 */
exports.onNewMessage = onDocumentCreated("chats/{chatId}/messages/{messageId}", async (event) => {
  const chatId = event.params.chatId;
  const msg = event.data.data();

  const chatSnap = await admin.firestore().collection("chats").doc(chatId).get();
  if (!chatSnap.exists) return;
  const chat = chatSnap.data() || {};

  const recipients = (chat.members || []).filter((uid) => uid !== msg.senderId);
  if (!recipients.length) return;

  const tokens = [];
  for (const uid of recipients) {
    const u = await admin.firestore().collection("users").doc(uid).get();
    if (!u.exists) continue;
    tokens.push(...(u.data()?.fcmTokens || []));
  }
  if (!tokens.length) return;

  await admin.messaging().sendEachForMulticast({
    tokens,
    notification: {
      title: chat.name || "Nova mensagem",
      body: msg.type === "text" ? "Nova mensagem" : `[${msg.type}]`,
    },
    data: { chatId },
  });
});
