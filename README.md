# Nostalgia-AI（安卓原生，Android 4.4.2+）

### 本项目由AI+linlelest进行开发

Nostalgia-AI 是一个 **原生 Android** 的 AI 对话客户端，目标是：

- **兼容 Android 4.4.2（API 19）及以上**
- 深浅色模式
- 侧边栏历史会话（增删改查）
- 支持 OpenAI 兼容 API（baseUrl/apikey/modelName/昵称，多 Provider、多 Model、多 Key）
- 支持模型组：对话页可选 **“组”** 或 **“直连模型”**
- 联网搜索（Tavily，支持多 key 轮询）
- 上传文件（图片/文档/音频/视频），多模态模型用标准 content parts；纯文本模型先提取文本再发送
- 导入/导出 JSON（可多选导出内容）

## 快速开始

1. 安装 APK（从 GitHub Release 下载）
2. 进入「设置」：
   - 添加 Provider（BaseUrl）
   - 添加 API Key
   - 添加模型（modelName + 昵称 + 是否多模态）
   - （可选）创建模型组并配置 provider 顺序与 model
   - （可选）配置 Tavily BaseUrl 与 Key
3. 回到对话页选择目标（组/直连模型），开始聊天

## GitHub Actions 自动编译

本项目通过 tag 自动编译 release APK：

请查看：[`GitHub编译.md`](./GitHub编译.md)

## 导入/导出

设置 → 导入/导出

- 支持导出：Provider/Key/Model/模型组/组 provider 顺序/Tavily/设置/历史
- 支持导入：追加（推荐）

> 说明：历史记录里的附件 URI 在不同设备上可能失效，导入后可能需要重新选择授权。
