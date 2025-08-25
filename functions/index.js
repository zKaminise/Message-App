// functions/index.js — Firebase Functions v5 (API v2) + Admin v12 + ESM
import { initializeApp } from "firebase-admin/app";
import { getFirestore, FieldPath, FieldValue } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";
import { getStorage } from "firebase-admin/storage";
import { onDocumentCreated } from "firebase-functions/v2/firestore";

initializeApp();

const db = getFirestore();
const messaging = getMessaging();

/**
 * 1) Marcadores de membresia no Storage quando um chat é criado
 *    (garante que todos os membros consigam acessar /chats/{chatId}/...)
 */
export const onChatCreatedMarkers = onDocumentCreated("chats/{chatId}", async (event) => {
  const snap = event.data;
  if (!snap) return;

  const data = snap.data();
  const members = Array.isArray(data?.members) ? data.members : [];
  const chatId = event.params.chatId;
  if (!chatId || members.length === 0) return;

  const bucket = getStorage().bucket();

  await Promise.all(
    members.map((uid) =>
      bucket
        .file(`chats/${chatId}/members/${uid}`)
        .save(Buffer.alloc(0), {
          resumable: false,
          metadata: { contentType: "application/octet-stream" },
        })
    )
  );
});

/**
 * 2) Push para membros (exceto o remetente) quando cria doc em /chats/{chatId}/messages/{messageId}
 */
export const onNewMessage = onDocumentCreated(
  { region: "us-central1", document: "chats/{chatId}/messages/{messageId}" },
  async (event) => {
    const snap = event.data;
    if (!snap) return;

    const chatId = event.params.chatId;
    const msg = snap.data();
    const senderId = msg.senderId;

    // Busca chat e filtra membros != remetente
    const chatSnap = await db.collection("chats").doc(chatId).get();
    if (!chatSnap.exists) return;

    const chat = chatSnap.data() || {};
    const members = (Array.isArray(chat.members) ? chat.members : []).filter((u) => u !== senderId);
    if (members.length === 0) return;

    // Junta tokens (em chunks de 10 por limitação de "in")
    let tokens = [];
    for (let i = 0; i < members.length; i += 10) {
      const chunk = members.slice(i, i + 10);
      const usersSnap = await db
        .collection("users")
        .where(FieldPath.documentId(), "in", chunk)
        .get();

      usersSnap.forEach((u) => {
        const t = u.get("fcmTokens") || [];
        tokens.push(...t);
      });
    }
    tokens = [...new Set(tokens)].filter(Boolean);
    if (tokens.length === 0) return;

    // Corpo da notificação
    let body = "[mensagem]";
    switch (msg.type) {
      case "text":
        body = "[texto]";
        break;
      case "image":
        body = "[imagem]";
        break;
      case "video":
        body = "[vídeo]";
        break;
      case "audio":
        body = "[áudio]";
        break;
    }
    const title = chat.name || "Nova mensagem";

    // Envia
    const resp = await messaging.sendEachForMulticast({
      tokens,
      notification: { title, body },
      data: { chatId },
    });

    // Limpa tokens inválidos
    const invalid = [];
    resp.responses.forEach((r, idx) => {
      if (!r.success) {
        const code = r.error?.code || "";
        if (code.includes("registration-token-not-registered")) {
          invalid.push(tokens[idx]);
        }
      }
    });

    if (invalid.length) {
      const usersSnap = await db.collection("users").get();
      const batch = db.batch();
      usersSnap.forEach((doc) => {
        invalid.forEach((tok) => {
          batch.update(doc.ref, { fcmTokens: FieldValue.arrayRemove(tok) });
        });
      });
      await batch.commit();
    }
  }
);
