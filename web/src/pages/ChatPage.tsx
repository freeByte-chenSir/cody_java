import ChatWindow from "../components/ChatWindow";

const DEFAULT_PROJECT_ID = "default";
const DEFAULT_PROJECT_NAME = "Chat";

export default function ChatPage() {
  return (
    <div className="chat-page">
      <main className="chat-main" style={{ width: "100%" }}>
        <ChatWindow
          projectId={DEFAULT_PROJECT_ID}
          projectName={DEFAULT_PROJECT_NAME}
          sessionId={null}
        />
      </main>
    </div>
  );
}
