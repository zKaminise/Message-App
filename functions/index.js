// Firebase Functions v5 (API v2) + Admin v12 + ESM
import { initializeApp } from "firebase-admin/app";
import { getFirestore, FieldPath, FieldValue } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";
import { onDocumentCreated } from "firebase-functions/v2/firestore";
import { getStorage } from "firebase-admin/storage";

initializeApp();

const db = getFirestore();
const messaging = getMessaging();
const bucket = getStorage().bucket();

/** Cria marcadores de membresia no Storage quando um chat nasce */
export const onChatCreatedMarkers = onDocumentCreated("chats/{chatId}", async (event) => {
  const data = event.data?.data();
  const members = Array.isArray(data?.members) ? data.members : [];
  const chatId = event.params.chatId;
  if (!chatId || members.length === 0) return;

  await Promise.all(
    members.map((uid) =>
      bucket.file(`chats/${chatId}/members/${uid}`).save(Buffer.alloc(0), {
        resumable: false,
        metadata: { contentType: "application/octet-stream" },
      })
    )
  );
});

/** Push quando chega mensagem nova */
export const onNewMessage = onDocumentCreated(
  "chats/{chatId}/messages/{messageId}",
  async (event) => {
    const msg = event.data?.data();
    if (!msg) return;

    const chatId = event.params.chatId;
    const senderId = msg.senderId;

    const chatSnap = await db.collection("chats").doc(chatId).get();
    if (!chatSnap.exists) return;

    const chat = chatSnap.data() || {};
    const members = (chat.members || []).filter((u) => u !== senderId);
    if (members.length === 0) return;

    // tokens em chunks de 10
    let tokens = [];
    for (let i = 0; i < members.length; i += 10) {
      const chunk = members.slice(i, i + 10);
      const usersSnap = await db
        .collection("users")
        .where(FieldPath.documentId(), "in", chunk)
        .get();

      usersSnap.forEach((u) => {
        const t = u.get("fcmTokens") || [];
        tokens = tokens.concat(t);
      });
    }
    tokens = Array.from(new Set(tokens)).filter(Boolean);
    if (tokens.length === 0) return;

    let body = "[mensagem]";
    if (msg.type === "text") body = "[texto]";
    else if (msg.type === "image") body = "[imagem]";
    else if (msg.type === "video") body = "[vídeo]";
    else if (msg.type === "audio") body = "[áudio]";

    const title = chat.name || "Nova mensagem";
    const resp = await messaging.sendEachForMulticast({
      tokens,
      notification: { title, body },
      data: { chatId },
    });

    // remove tokens inválidos
    const invalid = [];
    resp.responses.forEach((r, idx) => {
      if (!r.success) {
        const code = r.error?.code || "";
        if (code.includes("registration-token-not-registered")) {
          invalid.push(tokens[idx]);
        }
      }
    });
    if (invalid.length > 0) {
      const allUsers = await db.collection("users").get();
      const batch = db.batch();
      allUsers.forEach((doc) => {
        invalid.forEach((tok) => {
          batch.update(doc.ref, { fcmTokens: FieldValue.arrayRemove(tok) });
        });
      });
      await batch.commit();
    }
  }
);
