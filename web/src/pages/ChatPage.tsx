import ChatWindow from "../components/ChatWindow";

const DEFAULT_PROJECT_ID = "default";
const DEFAULT_PROJECT_NAME = "Chat";

/**
 * TODO 多会话支持 (multi-session UI):
 *   1. 侧边栏会话列表 — 展示历史会话, 支持点击切换、新建、删除、重命名
 *      - 数据源: GET /sessions 获取列表, POST /sessions 新建, DELETE /sessions/{id} 删除
 *      - 后端 API (SessionController) 已就绪, 前端只需对接
 *   2. 历史消息回显 — 切换会话时加载该会话的所有历史消息
 *      - 数据源: GET /sessions/{id} 返回会话详情, 需扩展后端返回 messages 列表
 *      - 或新增 GET /sessions/{id}/messages 端点
 *   3. 当前会话 id 管理 — ChatPage 维护 activeSessionId 状态, 传给 ChatWindow
 *      - ChatWindow 的 sessionId 目前固定为 null, 需改为传入有效 sessionId
 *      - 新消息发送后调用 POST /sessions 创建会话 (如会话尚不存在)
 *   4. 路由支持 — 可考虑 /chat/:sessionId 路由, 支持直接链接到特定会话
 */
export default function ChatPage() {
  return (
    <div className="chat-page">
      {/* TODO 多会话: 左侧添加 <SessionSidebar /> 展示会话列表, 支持切换/新建/删除 */}
      <main className="chat-main" style={{ width: "100%" }}>
        <ChatWindow
          projectId={DEFAULT_PROJECT_ID}
          projectName={DEFAULT_PROJECT_NAME}
          sessionId={null}  {/* TODO 多会话: 改为动态 activeSessionId */}
        />
      </main>
    </div>
  );
}
