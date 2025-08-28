#  Message App (WhatsApp Clone with Firebase)

Mensageiro simples com 1:1 e grupos, **envio de mídia**, **mensagens fixadas**, **busca**, **apagar para todos/só pra mim**, **esconder conversa**, **sair/excluir grupo**, **perfil**, **contatos** — tudo com Firebase (Auth, Firestore, Storage).

---

## Funcionalidades

- **Chats diretos e grupos**
- **Envio de mídia**: imagens, vídeos, áudios e arquivos (Firebase Storage)
- **Visual por dia (sticky headers)** e busca no chat
- **Mensagem fixada**
- **Status**: entregue/lido
- **Excluir mensagem**
  - **Para mim** (some só para o usuário)
  - **Para todos** (aparece “Mensagem apagada” para ambos)
- **Esconder conversa** (só para mim) e **restaurar**
- **Grupos**: sair do grupo; dono pode **apagar grupo para todos**
- **Perfil e contatos**
- **Preview de última mensagem** na Home (decodifica se estiver criptografada) 

---

## 🛠️ Tecnologias Utilizadas

- **Kotlin** – Linguagem principal  
- **Jetpack Compose** – Construção da UI declarativa  
- **Firebase** – Authentication, Cloud Firestore, Cloud Storage

---

## 📱 Instalação e Testes

### 1. Instalar via APK
Você pode baixar e instalar o app diretamente:  
👉 [Baixar APK](./apk/app-debug.apk)

### 2. Clonar e rodar no Android Studio
```bash
git clone https://github.com/zKaminise/Message-app.git
```
Abra o projeto no Android Studio

Conecte um dispositivo físico ou inicie um emulador

Clique em Run ▶

---

## Configurações do Firebase

 - Criar o Projeto e vincular o "google-services.json" no Projeto
 - Criação >> Authentication >> Ativar Email/senha e Smartphone
 - Criação >> Firestore Database >> Criar Banco de Dados >> Necessário configurar Coleção users e chats e uma subcoleção messages dentro de chats
 - Criação >> Storage >> Aqui não é necessário criar pastas

---

## Prints do Aplicativo

| Login | Home | Chat | Info do Chat | Contatos |
|-------|------|------|--------------|----------|
|<img width="378" height="757" alt="image" src="https://github.com/user-attachments/assets/6094aced-f951-40d1-8141-e398a0c6e0dc" />| <img width="377" height="760" alt="image" src="https://github.com/user-attachments/assets/416bf0d2-0224-4a95-8181-31f245011650" />| <img width="378" height="773" alt="image" src="https://github.com/user-attachments/assets/6d5ce2e1-a723-4881-a42d-9e9d71b59cbb" />| <img width="379" height="762" alt="image" src="https://github.com/user-attachments/assets/b2869cc1-fec7-4024-8368-22ee58d3d855" />| <img width="378" height="756" alt="image" src="https://github.com/user-attachments/assets/65de5999-a15c-4a15-9a11-c7a12f996ed4" />|

---

Desenvolvido como parte da disciplina de Programação para Dispositivos Móveis (PDM) – Universidade Federal de Uberlândia (UFU).
Alunos: 
- Gabriel Misao
- Caroline Cortes
- Angelo Toshio

