// SuperBizAgent 前端应用
class SuperBizAgentApp {
    constructor() {
        // R18: 同域 API（用户从统一域名访问，Nginx 反代到 gateway）
        // 浏览器实际访问 http://localhost:8080/，Nginx 把 /api/* 反代到 gateway-service:9000
        this.apiBaseUrl = '/api';
        this.currentMode = 'stream';
        this.sessionId = this.generateSessionId();
        this.isStreaming = false;
        this.abortController = null;
        this.currentAssistantMessageId = null;
        this.currentStreamingElement = null;
        this.currentProcessPanel = null;
        this.currentProcessSteps = new Map();
        this.currentProcessStepData = [];
        this.accessToken = localStorage.getItem('access_token') || '';
        this.refreshToken = localStorage.getItem('refresh_token') || '';
        this.currentChatHistory = []; // 当前对话的消息历史
        this.chatHistories = this.loadChatHistories(); // 所有历史对话
        this.isCurrentChatFromHistory = false; // 标记当前对话是否是从历史记录加载的
        this.citationCache = new Map();
        
        this.initializeElements();
        this.bindEvents();
        this.updateUI();
        this.initMarkdown();
        this.checkAndSetCentered();
        this.renderChatHistory();
        this.loadCurrentUser();
    }

    async apiFetch(url, options = {}, retry = true) {
        const headers = new Headers(options.headers || {});
        if (this.accessToken) {
            headers.set('Authorization', `Bearer ${this.accessToken}`);
        }
        if (!headers.has('Content-Type') && !(options.body instanceof FormData)) {
            headers.set('Content-Type', 'application/json');
        }
        const response = await fetch(url, { ...options, headers });
        if (response.status === 401 && retry && this.refreshToken) {
            const refreshResponse = await fetch('/api/auth/refresh', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ refresh_token: this.refreshToken })
            });
            if (refreshResponse.ok) {
                this.saveTokens(await refreshResponse.json());
                return this.apiFetch(url, options, false);
            }
        }
        if (response.status === 401) {
            this.clearTokens();
            this.showNotification('请先登录', 'warning');
        }
        return response;
    }

    saveTokens(pair) {
        this.accessToken = pair.access_token;
        this.refreshToken = pair.refresh_token;
        localStorage.setItem('access_token', this.accessToken);
        localStorage.setItem('refresh_token', this.refreshToken);
    }

    clearTokens() {
        this.accessToken = '';
        this.refreshToken = '';
        localStorage.removeItem('access_token');
        localStorage.removeItem('refresh_token');
    }

    async loadCurrentUser() {
        if (!this.accessToken) {
            this.updateAuthUI(null);
            return;
        }
        const response = await this.apiFetch('/api/auth/me');
        if (!response.ok) {
            this.updateAuthUI(null);
            return;
        }
        this.updateAuthUI(await response.json());
        await this.loadServerConversations();
    }

    updateAuthUI(user) {
        const authUser = document.getElementById('authUser');
        const adminLink = document.getElementById('adminLink');
        if (authUser) authUser.textContent = user ? '' : '请登录后继续';
        if (adminLink) adminLink.style.display = user ? 'inline-flex' : 'none';
        if (this.loginScreen) this.loginScreen.style.display = user ? 'none' : 'flex';
        if (this.appLayout) this.appLayout.style.display = user ? 'flex' : 'none';
    }

    async loginOrRegister(path) {
        const username = document.getElementById('usernameInput')?.value.trim();
        const password = document.getElementById('passwordInput')?.value;
        if (!username || !password) {
            this.showNotification('请输入用户名和密码', 'warning');
            return;
        }
        const response = await fetch(`/api/auth/${path}`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username, password })
        });
        if (!response.ok) {
            this.showNotification(path === 'login' ? '登录失败' : '注册失败', 'error');
            return;
        }
        if (path === 'register') {
            return this.loginOrRegister('login');
        }
        this.saveTokens(await response.json());
        await this.loadCurrentUser();
        this.showNotification('登录成功', 'success');
    }

    async logout() {
        if (this.refreshToken) {
            await fetch('/api/auth/logout', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ refresh_token: this.refreshToken })
            }).catch(() => {});
        }
        this.clearTokens();
        this.currentChatHistory = [];
        this.chatHistories = [];
        this.sessionId = this.generateSessionId();
        this.resetCurrentProcessTracking();
        if (this.chatMessages) this.chatMessages.innerHTML = '';
        this.renderChatHistory();
        this.updateAuthUI(null);
        this.showNotification('已退出登录', 'success');
    }

    // 初始化Markdown配置
    initMarkdown() {
        // 等待 marked 库加载完成
        const checkMarked = () => {
            if (typeof marked !== 'undefined') {
                try {
                    // 配置marked选项
                    marked.setOptions({
                        breaks: true,  // 支持GFM换行
                        gfm: true,     // 启用GitHub风格的Markdown
                        headerIds: false,
                        mangle: false
                    });

                    // 配置代码高亮
                    if (typeof hljs !== 'undefined') {
                        marked.setOptions({
                            highlight: function(code, lang) {
                                if (lang && hljs.getLanguage(lang)) {
                                    try {
                                        return hljs.highlight(code, { language: lang }).value;
                                    } catch (err) {
                                        console.error('代码高亮失败:', err);
                                    }
                                }
                                return code;
                            }
                        });
                    }
                    console.log('Markdown 渲染库初始化成功');
                } catch (e) {
                    console.error('Markdown 配置失败:', e);
                }
            } else {
                // 如果 marked 还没加载，等待一段时间后重试
                setTimeout(checkMarked, 100);
            }
        };
        checkMarked();
    }

    // 安全地渲染 Markdown
    renderMarkdown(content) {
        if (!content) return '';
        
        // 检查 marked 是否可用
        if (typeof marked === 'undefined') {
            console.warn('marked 库未加载，使用纯文本显示');
            return this.escapeHtml(content);
        }
        
        try {
            const html = marked.parse(content);
            return html;
        } catch (e) {
            console.error('Markdown 渲染失败:', e);
            return this.escapeHtml(content);
        }
    }

    // 高亮代码块
    highlightCodeBlocks(container) {
        if (typeof hljs !== 'undefined' && container) {
            try {
                container.querySelectorAll('pre code').forEach((block) => {
                    if (!block.classList.contains('hljs')) {
                        hljs.highlightElement(block);
                    }
                });
            } catch (e) {
                console.error('代码高亮失败:', e);
            }
        }
    }

    cacheMessageCitations(messageId, citations = []) {
        if (!messageId || !Array.isArray(citations) || citations.length === 0) {
            return;
        }
        this.citationCache.set(messageId, citations);
    }

    buildHistoryMessage(type, content, options = {}) {
        return {
            type: type,
            content: content,
            timestamp: options.timestamp || new Date().toISOString(),
            messageId: options.messageId || options.id || null,
            citations: Array.isArray(options.citations) ? options.citations : [],
            processSteps: this.normalizeProcessSteps(options.processSteps || options.process_steps || [])
        };
    }

    normalizeHistoryMessage(message = {}) {
        const rawType = message.type || (message.role === 'user' ? 'user' : 'assistant');
        const type = rawType === 'bot' ? 'assistant' : rawType;
        return this.buildHistoryMessage(type, message.content || '', {
            timestamp: message.timestamp || message.created_at,
            messageId: message.messageId || message.id,
            citations: message.citations || [],
            processSteps: message.processSteps || message.process_steps || []
        });
    }

    normalizeProcessSteps(processSteps = []) {
        if (!Array.isArray(processSteps)) {
            return [];
        }
        return processSteps
            .filter((step) => step && typeof step === 'object' && step.node)
            .map((step) => ({
                node: String(step.node),
                status: String(step.status || 'running'),
                message: String(step.message || '')
            }));
    }

    recordCurrentProcessStep(node, status, message) {
        if (!node) {
            return;
        }
        const normalized = {
            node: String(node),
            status: String(status || 'running'),
            message: String(message || '')
        };
        const existingIndex = this.currentProcessStepData.findIndex((step) => step.node === normalized.node);
        if (existingIndex >= 0) {
            this.currentProcessStepData.splice(existingIndex, 1, normalized);
        } else {
            this.currentProcessStepData.push(normalized);
        }
    }

    snapshotCurrentProcessSteps() {
        return this.normalizeProcessSteps(this.currentProcessStepData);
    }

    resetCurrentProcessTracking() {
        this.currentProcessPanel = null;
        this.currentProcessSteps = new Map();
        this.currentProcessStepData = [];
    }

    decorateCitationMarkers(container, citations = [], messageId = '') {
        if (!container || !Array.isArray(citations) || citations.length === 0) {
            return;
        }
        const citationMap = new Map(citations.map(item => [Number(item.index), item]));
        const walker = document.createTreeWalker(container, NodeFilter.SHOW_TEXT, {
            acceptNode: (node) => {
                if (!node.textContent || !/\[\d+\]/.test(node.textContent)) {
                    return NodeFilter.FILTER_REJECT;
                }
                const parent = node.parentElement;
                if (!parent || parent.closest('pre, code, .citation-link')) {
                    return NodeFilter.FILTER_REJECT;
                }
                return NodeFilter.FILTER_ACCEPT;
            }
        });
        const textNodes = [];
        while (walker.nextNode()) {
            textNodes.push(walker.currentNode);
        }

        textNodes.forEach((node) => {
            const text = node.textContent || '';
            const pattern = /\[(\d+)\]/g;
            const fragment = document.createDocumentFragment();
            let cursor = 0;
            let lastCitationKey = null;
            let lastWasCitation = false;
            let replaced = false;
            let match;

            while ((match = pattern.exec(text)) !== null) {
                const leadingText = text.slice(cursor, match.index);
                if (leadingText) {
                    fragment.appendChild(document.createTextNode(leadingText));
                    if (leadingText.trim()) {
                        lastCitationKey = null;
                        lastWasCitation = false;
                    }
                }

                const citation = citationMap.get(Number(match[1]));
                if (!citation) {
                    fragment.appendChild(document.createTextNode(match[0]));
                    lastCitationKey = null;
                    lastWasCitation = false;
                    cursor = match.index + match[0].length;
                    continue;
                }

                const citationKey = `${citation.document_id || citation.document_name || 'unknown'}:${citation.chunk_id || citation.index}`;
                const separatedOnlyByWhitespace = !leadingText || leadingText.trim() === '';
                if (!(lastWasCitation && separatedOnlyByWhitespace && lastCitationKey === citationKey)) {
                    fragment.appendChild(this.createCitationButton(citation, messageId));
                    replaced = true;
                }
                lastCitationKey = citationKey;
                lastWasCitation = true;
                cursor = match.index + match[0].length;
            }

            const trailingText = text.slice(cursor);
            if (trailingText) {
                fragment.appendChild(document.createTextNode(trailingText));
            }

            if (replaced) {
                node.parentNode.replaceChild(fragment, node);
            }
        });
    }

    createCitationButton(citation, messageId = '') {
        const sup = document.createElement('sup');
        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'citation-link';
        button.textContent = `[${citation.index}]`;
        button.dataset.citationIndex = String(citation.index);
        if (messageId) {
            button.dataset.messageId = messageId;
        }
        sup.appendChild(button);
        return sup;
    }

    async fetchMessageCitations(messageId) {
        if (!messageId) {
            return [];
        }
        if (this.citationCache.has(messageId)) {
            return this.citationCache.get(messageId) || [];
        }
        const response = await this.apiFetch(`${this.apiBaseUrl}/messages/${messageId}/citations`);
        if (!response.ok) {
            throw new Error('获取引用详情失败');
        }
        const payload = await response.json();
        const citations = payload.data || [];
        this.cacheMessageCitations(messageId, citations);
        return citations;
    }

    async handleCitationClick(button) {
        const messageElement = button.closest('.message');
        const messageId = button.dataset.messageId || messageElement?.dataset.messageId || '';
        const citationIndex = Number(button.dataset.citationIndex || 0);
        if (!messageId || !citationIndex) {
            this.showNotification('当前引用详情暂不可用', 'warning');
            return;
        }

        try {
            const cached = messageElement?._citations || [];
            const citations = cached.length ? cached : await this.fetchMessageCitations(messageId);
            const citation = citations.find(item => Number(item.index) === citationIndex);
            if (!citation) {
                this.showNotification('未找到对应引用详情', 'warning');
                return;
            }
            if (messageElement) {
                messageElement._citations = citations;
            }
            this.showCitationModal(citation);
        } catch (error) {
            console.error('加载引用详情失败:', error);
            this.showNotification(error.message || '加载引用详情失败', 'error');
        }
    }

    showCitationModal(citation) {
        if (!this.citationModal) {
            return;
        }
        if (this.citationModalIndex) {
            this.citationModalIndex.textContent = `[${citation.index}]`;
        }
        if (this.citationModalDoc) {
            this.citationModalDoc.textContent = citation.document_name || '未知文档';
        }
        if (this.citationModalSection) {
            this.citationModalSection.textContent = citation.section_path || '未标注章节';
        }
        if (this.citationModalVectorScore) {
            this.citationModalVectorScore.textContent = this.formatCitationScore(citation.vector_score);
        }
        if (this.citationModalRerankScore) {
            this.citationModalRerankScore.textContent = this.formatCitationScore(citation.rerank_score);
        }
        if (this.citationModalContent) {
            this.citationModalContent.textContent = citation.content_preview || '暂无切片内容';
        }
        if (this.citationModalDownload) {
            if (citation.download_url) {
                this.citationModalDownload.href = citation.download_url;
                this.citationModalDownload.style.display = 'inline-flex';
            } else {
                this.citationModalDownload.removeAttribute('href');
                this.citationModalDownload.style.display = 'none';
            }
        }
        this.citationModal.hidden = false;
        document.body.classList.add('modal-open');
    }

    hideCitationModal() {
        if (!this.citationModal) {
            return;
        }
        this.citationModal.hidden = true;
        document.body.classList.remove('modal-open');
    }

    formatCitationScore(score) {
        if (score === null || score === undefined || score === '') {
            return '暂无';
        }
        const numeric = Number(score);
        return Number.isFinite(numeric) ? numeric.toFixed(4) : '暂无';
    }

    // 初始化DOM元素
    initializeElements() {
        // 侧边栏元素
        this.sidebar = document.querySelector('.sidebar');
        this.newChatBtn = document.getElementById('newChatBtn');
        this.loginScreen = document.getElementById('loginScreen');
        this.appLayout = document.getElementById('appLayout');
        this.loginBtn = document.getElementById('loginBtn');
        this.registerBtn = document.getElementById('registerBtn');
        this.logoutBtn = document.getElementById('logoutBtn');
        
        // 输入区域元素
        this.messageInput = document.getElementById('messageInput');
        this.sendButton = document.getElementById('sendButton');
        this.modeSelectorBtn = document.getElementById('modeSelectorBtn');
        this.modeDropdown = document.getElementById('modeDropdown');
        this.currentModeText = document.getElementById('currentModeText');
        
        // 聊天区域元素
        this.chatMessages = document.getElementById('chatMessages');
        this.loadingOverlay = document.getElementById('loadingOverlay');
        this.chatContainer = document.querySelector('.chat-container');
        this.welcomeGreeting = document.getElementById('welcomeGreeting');
        this.chatHistoryList = document.getElementById('chatHistoryList');
        this.citationModal = document.getElementById('citationModal');
        this.citationModalClose = document.getElementById('citationModalClose');
        this.citationModalIndex = document.getElementById('citationModalIndex');
        this.citationModalDoc = document.getElementById('citationModalDoc');
        this.citationModalSection = document.getElementById('citationModalSection');
        this.citationModalVectorScore = document.getElementById('citationModalVectorScore');
        this.citationModalRerankScore = document.getElementById('citationModalRerankScore');
        this.citationModalContent = document.getElementById('citationModalContent');
        this.citationModalDownload = document.getElementById('citationModalDownload');
        
        // 初始化时检查是否需要居中
        this.checkAndSetCentered();
    }

    // 绑定事件监听器
    bindEvents() {
        // 新建对话
        if (this.newChatBtn) {
            this.newChatBtn.addEventListener('click', () => this.newChat());
        }
        if (this.loginBtn) {
            this.loginBtn.addEventListener('click', () => this.loginOrRegister('login'));
        }
        if (this.registerBtn) {
            this.registerBtn.addEventListener('click', () => this.loginOrRegister('register'));
        }
        if (this.logoutBtn) {
            this.logoutBtn.addEventListener('click', () => this.logout());
        }
        
        // 兼容旧页面结构：如果存在模式选择器，也统一按流式思考处理。
        if (this.modeSelectorBtn) {
            this.modeSelectorBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                this.toggleModeDropdown();
            });
        }
        
        // 下拉菜单项点击
        const dropdownItems = document.querySelectorAll('.dropdown-item');
        dropdownItems.forEach(item => {
            item.addEventListener('click', (e) => {
                const mode = item.getAttribute('data-mode');
                this.selectMode(mode);
                this.closeModeDropdown();
            });
        });
        
        // 点击外部关闭下拉菜单
        document.addEventListener('click', (e) => {
            if (this.modeSelectorBtn && this.modeDropdown &&
                !this.modeSelectorBtn.contains(e.target) &&
                !this.modeDropdown.contains(e.target)) {
                this.closeModeDropdown();
            }
        });
        
        // 发送消息
        if (this.sendButton) {
            this.sendButton.addEventListener('click', () => this.sendMessage());
        }
        
        if (this.messageInput) {
            this.messageInput.addEventListener('keydown', (e) => {
                if (e.key === 'Enter' && !e.shiftKey) {
                    e.preventDefault();
                    this.sendMessage();
                }
            });
            this.messageInput.addEventListener('input', () => {
                this.messageInput.style.height = 'auto';
                this.messageInput.style.height = `${Math.min(this.messageInput.scrollHeight, 180)}px`;
            });
        }

        if (this.chatMessages) {
            this.chatMessages.addEventListener('click', (e) => {
                const citationButton = e.target.closest('.citation-link');
                if (citationButton) {
                    e.preventDefault();
                    this.handleCitationClick(citationButton);
                }
            });
        }

        if (this.citationModal) {
            this.citationModal.addEventListener('click', (e) => {
                if (e.target.hasAttribute('data-close-citation')) {
                    this.hideCitationModal();
                }
            });
        }

        if (this.citationModalClose) {
            this.citationModalClose.addEventListener('click', () => this.hideCitationModal());
        }

        document.addEventListener('keydown', (e) => {
            if (e.key === 'Escape' && this.citationModal && !this.citationModal.hidden) {
                this.hideCitationModal();
            }
        });
        
    }

    // 新建对话
    newChat() {
        if (this.isStreaming) {
            this.showNotification('请等待当前对话完成后再新建对话', 'warning');
            return;
        }
        
        // 如果当前有对话内容，且不是从历史记录加载的，才保存为新的历史对话
        // 如果是从历史记录加载的，只需要更新该历史记录
        if (this.currentChatHistory.length > 0) {
            if (this.isCurrentChatFromHistory) {
                // 当前对话是从历史记录加载的，更新该历史记录
                this.updateCurrentChatHistory();
            } else {
                // 当前对话是新对话，保存为新的历史对话
                this.saveCurrentChat();
            }
        }
        
        // 停止所有进行中的操作
        this.isStreaming = false;
        
        // 清空输入框
        if (this.messageInput) {
            this.messageInput.value = '';
        }
        
        // 清空当前对话历史
        this.currentChatHistory = [];
        this.resetCurrentProcessTracking();
        
        // 重置标记
        this.isCurrentChatFromHistory = false;
        
        // 清空聊天记录
        if (this.chatMessages) {
            this.chatMessages.innerHTML = '';
        }
        
        // 生成新的会话ID
        this.sessionId = this.generateSessionId();
        
        // 默认使用“思考过程 + 流式回答”的统一模式。
        this.currentMode = 'stream';
        this.updateUI();
        
        // 重新设置居中样式（确保对话框居中显示）
        this.checkAndSetCentered();
        
        // 确保容器有过渡动画
        if (this.chatContainer) {
            this.chatContainer.style.transition = 'all 0.5s ease';
        }
        
        // 更新历史对话列表
        this.renderChatHistory();
    }
    
    // 保存当前对话到历史记录（新建）
    saveCurrentChat() {
        if (this.currentChatHistory.length === 0) {
            return;
        }
        
        // 检查是否已存在相同ID的历史记录
        const existingIndex = this.chatHistories.findIndex(h => h.id === this.sessionId);
        if (existingIndex !== -1) {
            // 如果已存在，更新而不是新建
            this.updateCurrentChatHistory();
            return;
        }
        
        // 获取对话标题（使用第一条用户消息的前30个字符）
        const firstUserMessage = this.currentChatHistory.find(msg => msg.type === 'user');
        const title = firstUserMessage ? 
            (firstUserMessage.content.substring(0, 30) + (firstUserMessage.content.length > 30 ? '...' : '')) : 
            '新对话';
        
        const chatHistory = {
            id: this.sessionId,
            title: title,
            messages: [...this.currentChatHistory],
            createdAt: new Date().toISOString(),
            updatedAt: new Date().toISOString()
        };
        
        // 添加到历史记录列表的开头
        this.chatHistories.unshift(chatHistory);
        
        // 限制历史记录数量（最多保存50条）
        if (this.chatHistories.length > 50) {
            this.chatHistories = this.chatHistories.slice(0, 50);
        }
        
        // 保存到localStorage
        this.saveChatHistories();
    }
    
    // 更新当前对话的历史记录
    updateCurrentChatHistory() {
        if (this.currentChatHistory.length === 0) {
            return;
        }
        
        const existingIndex = this.chatHistories.findIndex(h => h.id === this.sessionId);
        if (existingIndex === -1) {
            // 如果不存在，调用保存方法
            this.saveCurrentChat();
            return;
        }
        
        // 更新现有的历史记录
        const history = this.chatHistories[existingIndex];
        history.messages = [...this.currentChatHistory];
        history.updatedAt = new Date().toISOString();
        
        // 如果标题需要更新（第一条消息改变了）
        const firstUserMessage = this.currentChatHistory.find(msg => msg.type === 'user');
        if (firstUserMessage) {
            const newTitle = firstUserMessage.content.substring(0, 30) + (firstUserMessage.content.length > 30 ? '...' : '');
            if (history.title !== newTitle) {
                history.title = newTitle;
            }
        }
        
        // 保存到localStorage
        this.saveChatHistories();
    }
    
    // 加载历史对话列表
    loadChatHistories() {
        try {
            const stored = localStorage.getItem('chatHistories');
            return stored ? JSON.parse(stored) : [];
        } catch (e) {
            console.error('加载历史对话失败:', e);
            return [];
        }
    }

    async loadServerConversations() {
        const response = await this.apiFetch('/api/conversations');
        if (!response.ok) return;
        const sessions = await response.json();
        this.chatHistories = sessions.map(item => ({
            id: item.id,
            title: item.title,
            messages: [],
            createdAt: item.created_at,
            updatedAt: item.updated_at
        }));
        this.saveChatHistories();
        this.renderChatHistory();
    }

    // 保存历史对话列表到localStorage
    saveChatHistories() {
        try {
            localStorage.setItem('chatHistories', JSON.stringify(this.chatHistories));
        } catch (e) {
            console.error('保存历史对话失败:', e);
        }
    }
    
    // 渲染历史对话列表
    renderChatHistory() {
        if (!this.chatHistoryList) {
            return;
        }
        
        this.chatHistoryList.innerHTML = '';
        
        if (this.chatHistories.length === 0) {
            return;
        }
        
        this.chatHistories.forEach((history, index) => {
            const historyItem = document.createElement('div');
            historyItem.className = 'history-item';
            historyItem.dataset.historyId = history.id;
            
            historyItem.innerHTML = `
                <div class="history-item-content">
                    <span class="history-item-title">${this.escapeHtml(history.title)}</span>
                </div>
                <button class="history-item-delete" data-history-id="${history.id}" title="删除">
                    <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                        <path d="M18 6L6 18M6 6L18 18" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
                    </svg>
                </button>
            `;
            
            // 点击历史项加载对话
            historyItem.addEventListener('click', (e) => {
                if (!e.target.closest('.history-item-delete')) {
                    this.loadChatHistory(history.id);
                }
            });
            
            // 删除历史对话
            const deleteBtn = historyItem.querySelector('.history-item-delete');
            deleteBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                this.deleteChatHistory(history.id);
            });
            
            this.chatHistoryList.appendChild(historyItem);
        });
    }
    
    // 加载历史对话
    async loadChatHistory(historyId) {
        const history = this.chatHistories.find(h => h.id === historyId);
        if (!history) {
            return;
        }
        
        // 如果当前有对话内容，且不是同一个对话，先保存
        if (this.currentChatHistory.length > 0 && this.sessionId !== historyId) {
            if (this.isCurrentChatFromHistory) {
                // 如果当前对话也是从历史记录加载的，更新它
                this.updateCurrentChatHistory();
            } else {
                // 如果当前对话是新对话，保存为新历史
                this.saveCurrentChat();
            }
        }
        
        try {
            // 从后端获取会话历史
            const response = await this.apiFetch(`/api/chat/session/${historyId}`);
            if (response.ok) {
                const data = await response.json();
                const backendHistory = data.history || [];
                
                // 更新会话ID
                this.sessionId = history.id;
                this.isCurrentChatFromHistory = true;
                
                // 清空并重新渲染消息
                if (this.chatMessages) {
                    this.resetCurrentProcessTracking();
                    this.chatMessages.innerHTML = '';
                    
                    // 如果后端有历史记录，使用后端的
                    if (backendHistory.length > 0) {
                        this.currentChatHistory = backendHistory.map((msg) => this.normalizeHistoryMessage(msg));
                        this.currentChatHistory.forEach((msg) => {
                            this.addMessage(msg.type, msg.content, false, false, msg);
                        });
                    } else {
                        // 否则使用localStorage的历史记录
                        this.currentChatHistory = (history.messages || []).map((msg) => this.normalizeHistoryMessage(msg));
                        this.currentChatHistory.forEach((msg) => {
                            this.addMessage(msg.type, msg.content, false, false, msg);
                        });
                    }
                }
            } else {
                // 如果后端请求失败，使用localStorage的历史记录
                console.warn('从后端加载历史失败，使用本地缓存');
                this.sessionId = history.id;
                this.currentChatHistory = (history.messages || []).map((msg) => this.normalizeHistoryMessage(msg));
                this.isCurrentChatFromHistory = true;
                
                if (this.chatMessages) {
                    this.resetCurrentProcessTracking();
                    this.chatMessages.innerHTML = '';
                    this.currentChatHistory.forEach((msg) => {
                        this.addMessage(msg.type, msg.content, false, false, msg);
                    });
                }
            }
        } catch (error) {
            console.error('加载会话历史失败:', error);
            // 出错时使用localStorage的历史记录
            this.sessionId = history.id;
            this.currentChatHistory = (history.messages || []).map((msg) => this.normalizeHistoryMessage(msg));
            this.isCurrentChatFromHistory = true;
            
            if (this.chatMessages) {
                this.resetCurrentProcessTracking();
                this.chatMessages.innerHTML = '';
                this.currentChatHistory.forEach((msg) => {
                    this.addMessage(msg.type, msg.content, false, false, msg);
                });
            }
        }
        
        // 更新UI
        this.checkAndSetCentered();
        this.renderChatHistory();
    }
    
    // 删除历史对话
    async deleteChatHistory(historyId) {
        try {
            // 调用后端API清空会话
            const response = await this.apiFetch('/api/chat/clear', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({
                    session_id: historyId
                })
            });

            if (!response.ok) {
                throw new Error('清空会话失败');
            }

            const result = await response.json();
            
            if (result.status === 'success') {
                // 从本地存储中删除
                this.chatHistories = this.chatHistories.filter(h => h.id !== historyId);
                this.saveChatHistories();
                this.renderChatHistory();
                
                // 如果删除的是当前对话，清空当前对话
                if (this.sessionId === historyId) {
                    this.currentChatHistory = [];
                    if (this.chatMessages) {
                        this.chatMessages.innerHTML = '';
                    }
                    this.sessionId = this.generateSessionId();
                    this.checkAndSetCentered();
                }
                
                this.showNotification('会话已清空', 'success');
            } else {
                throw new Error(result.message || '清空会话失败');
            }
        } catch (error) {
            console.error('删除历史对话失败:', error);
            this.showNotification('删除失败: ' + error.message, 'error');
        }
    }

    // 切换模式下拉菜单
    toggleModeDropdown() {
        if (this.modeSelectorBtn && this.modeDropdown) {
            const wrapper = this.modeSelectorBtn.closest('.mode-selector-wrapper');
            if (wrapper) {
                wrapper.classList.toggle('active');
            }
        }
    }

    // 关闭模式下拉菜单
    closeModeDropdown() {
        if (this.modeSelectorBtn && this.modeDropdown) {
            const wrapper = this.modeSelectorBtn.closest('.mode-selector-wrapper');
            if (wrapper) {
                wrapper.classList.remove('active');
            }
        }
    }

    // 选择模式
    selectMode(mode) {
        if (this.isStreaming) {
            this.showNotification('请等待当前对话完成后再切换模式', 'warning');
            return;
        }
        
        this.currentMode = mode;
        this.updateUI();
        
        const modeNames = {
            'quick': '思考',
            'stream': '思考'
        };
        
        this.showNotification(`已切换到${modeNames[mode]}模式`, 'info');
    }

    // 更新UI
    updateUI() {
        // 更新模式选择器显示
        if (this.currentModeText) {
            const modeNames = {
                'quick': '思考',
                'stream': '思考'
            };
            this.currentModeText.textContent = modeNames[this.currentMode] || '思考';
        }
        
        // 更新下拉菜单选中状态
        const dropdownItems = document.querySelectorAll('.dropdown-item');
        dropdownItems.forEach(item => {
            const mode = item.getAttribute('data-mode');
            if (mode === this.currentMode) {
                item.classList.add('active');
            } else {
                item.classList.remove('active');
            }
        });
        
        // 更新发送按钮状态
        if (this.sendButton) {
            this.sendButton.disabled = false;
            this.sendButton.title = this.isStreaming ? '停止生成' : '发送';
            this.sendButton.setAttribute('aria-label', this.isStreaming ? '停止生成' : '发送');
            this.sendButton.classList.toggle('is-stopping', this.isStreaming);
            this.setSendButtonIcon(this.isStreaming ? 'stop' : 'send');
        }
        
        // 更新输入框状态
        if (this.messageInput) {
            this.messageInput.disabled = this.isStreaming;
            this.messageInput.placeholder = '问问智能OnCall助手';
        }
    }

    setSendButtonIcon(state) {
        if (!this.sendButton) return;
        if (state === 'stop') {
            this.sendButton.innerHTML = `
                <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                    <rect x="7" y="7" width="10" height="10" rx="2" fill="currentColor"/>
                </svg>
            `;
            return;
        }
        this.sendButton.innerHTML = `
            <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                <path d="M22 2L11 13M22 2L15 22L11 13M22 2L2 9L11 13" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
        `;
    }

    getProcessNodeLabel(node) {
        const labels = {
            intent_detection: '意图识别',
            terminology_matching: '术语匹配',
            knowledge_retrieval: '知识库召回',
            llm_call: '调用大模型',
            answer_generation: '答案生成'
        };
        return labels[node] || node;
    }

    createProcessPanel(hostMessage = null, options = {}) {
        const trackAsCurrent = options.trackAsCurrent !== false;
        const panel = document.createElement('div');
        panel.className = 'process-panel';
        panel.innerHTML = `
            <button class="process-panel-header" type="button">
                <span>思考过程</span>
                <span class="process-panel-toggle">折叠</span>
            </button>
            <div class="process-panel-body"></div>
        `;
        const header = panel.querySelector('.process-panel-header');
        header.addEventListener('click', () => this.toggleProcessPanel(panel));
        const wrapper = hostMessage?.querySelector('.message-content-wrapper');
        if (wrapper) {
            const messageContent = wrapper.querySelector('.message-content');
            wrapper.insertBefore(panel, messageContent || null);
        } else if (this.chatMessages) {
            this.chatMessages.appendChild(panel);
        }
        panel._processStepRows = new Map();
        this.scrollToBottom();
        if (options.collapsed) {
            this.collapseProcessPanel(panel);
        }
        if (trackAsCurrent) {
            this.currentProcessPanel = panel;
            this.currentProcessSteps = panel._processStepRows;
            this.currentProcessStepData = [];
        }
        return panel;
    }

    attachProcessPanelToMessage(element) {
        if (!this.currentProcessPanel || !element) return;
        const wrapper = element.querySelector('.message-content-wrapper');
        if (!wrapper) return;
        wrapper.appendChild(this.currentProcessPanel);
        this.scrollToBottom();
    }

    setAssistantMessageContent(messageElement, content, options = {}) {
        if (!messageElement) return;
        messageElement.classList.remove('streaming');
        const messageId = options.messageId || options.id || messageElement.dataset.messageId || '';
        const citations = Array.isArray(options.citations) ? options.citations : [];
        if (messageId) {
            messageElement.dataset.messageId = messageId;
        }
        messageElement._citations = citations;
        this.cacheMessageCitations(messageId, citations);
        const messageContent = messageElement.querySelector('.message-content');
        if (!messageContent) return;
        messageContent.innerHTML = this.renderMarkdown(content || '');
        this.decorateCitationMarkers(messageContent, citations, messageId);
        this.highlightCodeBlocks(messageContent);
    }

    toggleProcessPanel(panel = this.currentProcessPanel) {
        if (!panel) return;
        const collapsed = panel.classList.toggle('collapsed');
        const toggle = panel.querySelector('.process-panel-toggle');
        if (toggle) toggle.textContent = collapsed ? '展开' : '折叠';
    }

    collapseProcessPanel(panel = this.currentProcessPanel) {
        if (!panel) return;
        panel.classList.add('collapsed');
        const toggle = panel.querySelector('.process-panel-toggle');
        if (toggle) toggle.textContent = '展开';
    }

    updateProcessStep(node, status, message, options = {}) {
        const panel = options.panel || this.currentProcessPanel || this.createProcessPanel(options.hostMessage || null);
        const body = panel.querySelector('.process-panel-body');
        if (!body) return;
        const stepRegistry = options.stepRegistry || panel._processStepRows || this.currentProcessSteps;
        let row = stepRegistry.get(node);
        if (!row) {
            row = document.createElement('div');
            row.className = 'process-step';
            row.innerHTML = `
                <span class="process-step-dot"></span>
                <div>
                    <strong>${this.escapeHtml(this.getProcessNodeLabel(node))}</strong>
                    <small></small>
                </div>
            `;
            body.appendChild(row);
            if (options.animate === false) {
                row.classList.add('visible');
            } else {
                requestAnimationFrame(() => row.classList.add('visible'));
            }
            stepRegistry.set(node, row);
        }
        row.className = `process-step visible ${status || 'running'}`;
        const detail = row.querySelector('small');
        if (detail) detail.textContent = message || '';
        if (panel === this.currentProcessPanel && !options.skipTracking) {
            this.recordCurrentProcessStep(node, status, message);
        }
        this.scrollToBottom();
    }

    renderProcessPanelFromHistory(messageElement, processSteps = []) {
        const normalizedSteps = this.normalizeProcessSteps(processSteps);
        if (!messageElement || normalizedSteps.length === 0) {
            return;
        }
        const panel = this.createProcessPanel(messageElement, { trackAsCurrent: false, collapsed: true });
        const stepRegistry = panel._processStepRows || new Map();
        normalizedSteps.forEach((step) => {
            this.updateProcessStep(step.node, step.status, step.message, {
                panel: panel,
                stepRegistry: stepRegistry,
                animate: false,
                skipTracking: true
            });
        });
    }

    completeQuickProcess(chatResponse) {
        const intent = chatResponse?.intent?.intent;
        const panel = this.currentProcessPanel;
        this.updateProcessStep('intent_detection', 'success', chatResponse?.intent?.reason || '已完成意图识别');
        if (intent === 'knowledge_qa') {
            this.updateProcessStep('terminology_matching', 'success', '已完成术语匹配');
            this.updateProcessStep(
                'knowledge_retrieval',
                chatResponse.no_evidence ? 'warning' : 'success',
                chatResponse.no_evidence ? '当前知识库未检索到足够依据' : '已完成知识库检索'
            );
            this.updateProcessStep(
                'answer_generation',
                'success',
                chatResponse.is_knowledge_grounded ? '答案生成完成' : '答案生成完成，已标记为非知识库依据'
            );
        } else {
            this.updateProcessStep('llm_call', 'success', '大模型调用完成');
            this.updateProcessStep('answer_generation', 'success', '答案生成完成');
        }
        setTimeout(() => this.collapseProcessPanel(panel), 500);
    }

    // 生成随机会话ID
    generateSessionId() {
        return 'session_' + Math.random().toString(36).substr(2, 9) + '_' + Date.now();
    }

    // 发送消息
    async sendMessage() {
        if (this.isStreaming && this.abortController) {
            const stoppedMessageId = this.currentAssistantMessageId;
            this.abortController.abort();
            if (stoppedMessageId) {
                this.apiFetch(`${this.apiBaseUrl}/chat/${stoppedMessageId}/stop`, {
                    method: 'POST'
                }).catch((error) => {
                    console.warn('同步停止状态失败:', error);
                });
            }
            if (this.currentStreamingElement) {
                const messageContent = this.currentStreamingElement.querySelector('.message-content');
                const existingText = messageContent ? messageContent.textContent.trim() : '';
                if (messageContent && !existingText) {
                    messageContent.textContent = '已停止生成';
                }
                this.currentStreamingElement.classList.remove('streaming');
            }
            this.isStreaming = false;
            this.currentAssistantMessageId = null;
            this.currentStreamingElement = null;
            this.collapseProcessPanel();
            this.updateUI();
            this.showNotification('已停止生成', 'success');
            return;
        }
        let message = '';
        if (this.messageInput) {
            message = this.messageInput.value.trim();
        }
        
        if (!message) {
            this.showNotification('请输入消息内容', 'warning');
            return;
        }

        if (this.isStreaming) {
            this.showNotification('请等待当前对话完成', 'warning');
            return;
        }

        // 显示用户消息
        this.addMessage('user', message);
        
        // 清空输入框
        if (this.messageInput) {
            this.messageInput.value = '';
        }

        // 设置发送状态
        this.isStreaming = true;
        this.updateUI();

        try {
            await this.sendStreamMessage(message);
        } catch (error) {
            console.error('发送消息失败:', error);
            this.addMessage('assistant', '抱歉，发送消息时出现错误：' + error.message);
        } finally {
            this.isStreaming = false;
            this.updateUI();
            
            // 如果当前对话是从历史记录加载的，更新历史记录
            if (this.isCurrentChatFromHistory && this.currentChatHistory.length > 0) {
                this.updateCurrentChatHistory();
                this.renderChatHistory(); // 更新历史对话列表显示
            }
        }
    }

    // 发送快速消息（普通对话）
    async sendQuickMessage(message) {
        const assistantMessageElement = this.addMessage('assistant', '', true, false);
        this.currentStreamingElement = assistantMessageElement;
        this.createProcessPanel(assistantMessageElement);
        this.updateProcessStep('intent_detection', 'running', '正在识别问题类型');
        
        try {
            const response = await this.apiFetch(`${this.apiBaseUrl}/chat`, {
                method: 'POST',
                body: JSON.stringify({
                    Id: this.sessionId,
                    Question: message
                })
            });

            if (!response.ok) {
                throw new Error(`HTTP错误: ${response.status}`);
            }

            const data = await response.json();
            console.log('[sendQuickMessage] 响应数据:', JSON.stringify(data));
            
            // 统一响应格式：检查 data.code 或 data.message 判断请求是否成功
            if (data.code === 200 || data.message === 'success') {
                // data.data 是 ChatResponse 对象
                const chatResponse = data.data;
                
                if (chatResponse && chatResponse.success) {
                    // 成功：添加实际响应消息（即使 answer 为空也显示）
                    this.sessionId = chatResponse.session_id || this.sessionId;
                    const answer = chatResponse.answer || '（无回复内容）';
                    this.setAssistantMessageContent(assistantMessageElement, answer, {
                        messageId: chatResponse.message_id,
                        citations: chatResponse.citations || []
                    });
                    this.completeQuickProcess(chatResponse);
                    this.currentChatHistory.push(this.buildHistoryMessage('assistant', answer, {
                        messageId: chatResponse.message_id,
                        citations: chatResponse.citations || [],
                        processSteps: this.snapshotCurrentProcessSteps()
                    }));
                } else if (chatResponse && chatResponse.errorMessage) {
                    // 业务错误
                    throw new Error(chatResponse.errorMessage);
                } else {
                    // 兜底：尝试显示任何可用内容
                    const fallbackAnswer = chatResponse?.answer || chatResponse?.errorMessage || '服务返回了空内容';
                    this.addMessage('assistant', fallbackAnswer);
                }
            } else {
                // HTTP 成功但业务失败
                throw new Error(data.message || '请求失败');
            }
        } catch (error) {
            this.updateProcessStep('answer_generation', 'failed', error.message || '生成失败');
            this.setAssistantMessageContent(assistantMessageElement, '抱歉，发送消息时出现错误：' + error.message);
            throw error;
        } finally {
            this.currentStreamingElement = null;
            this.resetCurrentProcessTracking();
        }
    }

    // 发送流式消息
    async sendStreamMessage(message) {
        const assistantMessageElement = this.addMessage('assistant', '', true);
        this.currentStreamingElement = assistantMessageElement;
        this.createProcessPanel(assistantMessageElement);
        this.updateProcessStep('intent_detection', 'running', '正在识别问题类型');
        try {
            this.abortController = new AbortController();
            this.currentAssistantMessageId = null;
            let streamingCitations = [];
            let streamingMessageId = '';
            const response = await this.apiFetch(`${this.apiBaseUrl}/chat_stream`, {
                method: 'POST',
                signal: this.abortController.signal,
                body: JSON.stringify({
                    Id: this.sessionId,
                    Question: message
                })
            });

            if (!response.ok) {
                throw new Error(`HTTP错误: ${response.status}`);
            }
            
            let fullResponse = '';

            // 处理流式响应
            const reader = response.body.getReader();
            const decoder = new TextDecoder();
            let buffer = '';
            let currentEvent = '';

            try {
                while (true) {
                    const { done, value } = await reader.read();
                    
                    if (done) {
                        // 流结束，使用统一的处理方法
                        this.handleStreamComplete(assistantMessageElement, fullResponse);
                        break;
                    }

                    // 解码数据并添加到缓冲区
                    buffer += decoder.decode(value, { stream: true });
                    
                    // 按行分割处理
                    const lines = buffer.split('\n');
                    // 保留最后一行（可能不完整）
                    buffer = lines.pop() || '';
                    
                    for (const line of lines) {
                        if (line.trim() === '') continue;
                        
                        console.log('[SSE调试] 收到行:', line);
                        
                        // 解析SSE格式
                        if (line.startsWith('id:')) {
                            console.log('[SSE调试] 解析到ID');
                            continue;
                        } else if (line.startsWith('event:')) {
                            // 兼容 "event:message" 和 "event: message" 两种格式
                            currentEvent = line.substring(6).trim();
                            console.log('[SSE调试] 解析到事件类型:', currentEvent);
                            // 注意：后端统一使用 "message" 事件名，真正的类型在 data 的 JSON 中
                            continue;
                        } else if (line.startsWith('data:')) {
                            // 兼容 "data:xxx" 和 "data: xxx" 两种格式
                            const rawData = line.substring(5).trim();
                            console.log('[SSE调试] 解析到数据, currentEvent:', currentEvent, ', rawData:', rawData);
                            
                            // 兼容旧格式 [DONE] 标记
                            if (rawData === '[DONE]') {
                                // 流结束标记，将内容转换为Markdown渲染
                                this.handleStreamComplete(assistantMessageElement, fullResponse);
                                return;
                            }
                            
                            // 处理 SSE 数据
                            try {
                                // 尝试解析为 SseMessage 格式的 JSON
                                const sseMessage = JSON.parse(rawData);
                                console.log('[SSE调试] 解析JSON成功:', sseMessage);
                                
                                if (sseMessage && typeof sseMessage.type === 'string') {
                                    if (sseMessage.type === 'message_created') {
                                        streamingMessageId = sseMessage.data?.assistant_message_id || '';
                                        this.currentAssistantMessageId = streamingMessageId || null;
                                    } else if (sseMessage.type === 'citation_created') {
                                        streamingCitations = sseMessage.data?.citations || [];
                                    } else if (sseMessage.type === 'node_status') {
                                        const nodeData = sseMessage.data || {};
                                        this.updateProcessStep(nodeData.node, nodeData.status, nodeData.message);
                                        if (nodeData.node === 'answer_generation' && nodeData.status === 'success') {
                                            const panel = this.currentProcessPanel;
                                            setTimeout(() => this.collapseProcessPanel(panel), 300);
                                        }
                                    } else if (sseMessage.type === 'content') {
                                        const content = this.extractSseText(sseMessage.data);
                                        fullResponse += content;
                                        console.log('[SSE调试] 添加内容:', content);
                                        
                                        // 实时渲染 Markdown
                                        if (assistantMessageElement) {
                                            const messageContent = assistantMessageElement.querySelector('.message-content');
                                            messageContent.innerHTML = this.renderMarkdown(fullResponse);
                                            // 高亮代码块
                                            this.highlightCodeBlocks(messageContent);
                                            this.scrollToBottom();
                                        }
                                    } else if (sseMessage.type === 'done') {
                                        console.log('[SSE调试] 收到done标记，流结束');
                                        const doneData = sseMessage.data || {};
                                        streamingMessageId = doneData.message_id || streamingMessageId;
                                        streamingCitations = doneData.citations || streamingCitations;
                                        const finalAnswer = this.extractSseText(doneData);
                                        if (!fullResponse && finalAnswer) {
                                            fullResponse = finalAnswer;
                                        }
                                        this.handleStreamComplete(assistantMessageElement, fullResponse, {
                                            messageId: streamingMessageId,
                                            citations: streamingCitations
                                        });
                                        this.currentAssistantMessageId = null;
                                        this.currentStreamingElement = null;
                                        return;
                                    } else if (sseMessage.type === 'error') {
                                        console.error('[SSE调试] 收到错误:', sseMessage.data);
                                        if (assistantMessageElement) {
                                            const messageContent = assistantMessageElement.querySelector('.message-content');
                                            messageContent.innerHTML = this.renderMarkdown('错误: ' + (this.extractSseText(sseMessage.data) || '未知错误'));
                                        }
                                        this.updateProcessStep('answer_generation', 'failed', this.extractSseText(sseMessage.data) || '生成失败');
                                        this.currentAssistantMessageId = null;
                                        this.currentStreamingElement = null;
                                        this.resetCurrentProcessTracking();
                                        return;
                                    }
                                } else {
                                    // 不是标准 SseMessage 格式，尝试兼容处理
                                    console.log('[SSE调试] 非标准格式，尝试兼容处理');
                                    fullResponse += rawData;
                                    if (assistantMessageElement) {
                                        const messageContent = assistantMessageElement.querySelector('.message-content');
                                        messageContent.innerHTML = this.renderMarkdown(fullResponse);
                                        this.highlightCodeBlocks(messageContent);
                                        this.scrollToBottom();
                                    }
                                }
                            } catch (e) {
                                // JSON 解析失败，尝试兼容旧格式
                                console.log('[SSE调试] JSON解析失败，使用兼容模式:', e.message);
                                if (rawData === '') {
                                    fullResponse += '\n';
                                } else {
                                    fullResponse += rawData;
                                }
                                
                                if (assistantMessageElement) {
                                    const messageContent = assistantMessageElement.querySelector('.message-content');
                                    messageContent.innerHTML = this.renderMarkdown(fullResponse);
                                    this.highlightCodeBlocks(messageContent);
                                    this.scrollToBottom();
                                }
                            }
                        }
                    }
                }
            } finally {
                reader.releaseLock();
            }
        } catch (error) {
            if (error.name === 'AbortError') {
                this.resetCurrentProcessTracking();
                return;
            }
            throw error;
        } finally {
            this.abortController = null;
        }
    }

    extractSseText(data) {
        if (data == null) return '';
        if (typeof data === 'string') return data;
        if (typeof data === 'number' || typeof data === 'boolean') return String(data);
        if (typeof data === 'object') {
            return data.text || data.answer || data.content || data.message || data.data || '';
        }
        return '';
    }

    // 添加消息到聊天界面
    addMessage(type, content, isStreaming = false, saveToHistory = true, options = {}) {
        // 检查是否是第一条消息，如果是则移除居中样式
        const isFirstMessage = this.chatMessages && this.chatMessages.querySelectorAll('.message').length === 0;
        
        // 保存消息到当前对话历史（如果不是流式消息且需要保存）
        if (!isStreaming && saveToHistory && content) {
            this.currentChatHistory.push(this.buildHistoryMessage(type, content, options));
        }
        
        const messageDiv = document.createElement('div');
        messageDiv.className = `message ${type}${isStreaming ? ' streaming' : ''}`;
        if (options.messageId || options.id) {
            messageDiv.dataset.messageId = options.messageId || options.id;
        }

        // 如果是assistant消息，添加头像图标
        if (type === 'assistant') {
            const messageAvatar = document.createElement('div');
            messageAvatar.className = 'message-avatar';
            messageAvatar.innerHTML = `
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                    <path d="M12 2L15.09 8.26L22 9.27L17 14.14L18.18 21.02L12 17.77L5.82 21.02L7 14.14L2 9.27L8.91 8.26L12 2Z" fill="white"/>
                </svg>
            `;
            messageDiv.appendChild(messageAvatar);
        }

        // 创建消息内容包装器
        const messageContentWrapper = document.createElement('div');
        messageContentWrapper.className = 'message-content-wrapper';

        const messageContent = document.createElement('div');
        messageContent.className = 'message-content';
        
        // 如果是assistant消息且不是流式消息，使用Markdown渲染
        if (type === 'assistant' && !isStreaming) {
            messageContent.innerHTML = '';
        } else {
            // 用户消息或流式消息使用纯文本
            messageContent.textContent = content;
        }

        messageContentWrapper.appendChild(messageContent);
        messageDiv.appendChild(messageContentWrapper);

        if (this.chatMessages) {
            this.chatMessages.appendChild(messageDiv);
            if (type === 'assistant' && !isStreaming) {
                this.setAssistantMessageContent(messageDiv, content, options);
                this.renderProcessPanelFromHistory(messageDiv, options.processSteps || options.process_steps || []);
            }
            
            // 如果是第一条消息，移除居中样式并添加动画
            if (isFirstMessage && this.chatContainer) {
                this.chatContainer.classList.remove('centered');
                // 添加动画类
                this.chatContainer.style.transition = 'all 0.5s ease';
            }
            
            this.scrollToBottom();
        }

        return messageDiv;
    }

    // 添加带加载动画的消息
    addLoadingMessage(content) {
        const messageDiv = document.createElement('div');
        messageDiv.className = 'message assistant';

        // 添加头像图标
        const messageAvatar = document.createElement('div');
        messageAvatar.className = 'message-avatar';
        messageAvatar.innerHTML = `
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                <path d="M12 2L15.09 8.26L22 9.27L17 14.14L18.18 21.02L12 17.77L5.82 21.02L7 14.14L2 9.27L8.91 8.26L12 2Z" fill="white"/>
            </svg>
        `;
        messageDiv.appendChild(messageAvatar);

        // 创建消息内容包装器
        const messageContentWrapper = document.createElement('div');
        messageContentWrapper.className = 'message-content-wrapper';

        const messageContent = document.createElement('div');
        messageContent.className = 'message-content loading-message-content';
        
        // 创建文本和动画容器
        const textSpan = document.createElement('span');
        textSpan.textContent = content;
        
        // 创建旋转动画图标
        const loadingIcon = document.createElement('span');
        loadingIcon.className = 'loading-spinner-icon';
        loadingIcon.innerHTML = `
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 18c-4.41 0-8-3.59-8-8s3.59-8 8-8 8 3.59 8 8-3.59 8-8 8z" fill="currentColor" opacity="0.2"/>
                <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10c1.54 0 3-.36 4.28-1l-1.5-2.6C13.64 19.62 12.84 20 12 20c-4.41 0-8-3.59-8-8s3.59-8 8-8c.84 0 1.64.38 2.18 1l1.5-2.6C13 2.36 12.54 2 12 2z" fill="currentColor"/>
            </svg>
        `;
        
        messageContent.appendChild(textSpan);
        messageContent.appendChild(loadingIcon);
        messageContentWrapper.appendChild(messageContent);
        messageDiv.appendChild(messageContentWrapper);

        if (this.chatMessages) {
            this.chatMessages.appendChild(messageDiv);
            
            // 如果是第一条消息，移除居中样式
            const isFirstMessage = this.chatMessages.querySelectorAll('.message').length === 1;
            if (isFirstMessage && this.chatContainer) {
                this.chatContainer.classList.remove('centered');
                this.chatContainer.style.transition = 'all 0.5s ease';
            }
            
            this.scrollToBottom();
        }

        return messageDiv;
    }
    
    // 检查并设置居中样式
    checkAndSetCentered() {
        if (this.chatMessages && this.chatContainer) {
            const hasMessages = this.chatMessages.querySelectorAll('.message').length > 0;
            if (!hasMessages) {
                this.chatContainer.classList.add('centered');
            } else {
                this.chatContainer.classList.remove('centered');
            }
        }
    }

    // 滚动到底部
    scrollToBottom() {
        if (this.chatMessages) {
            this.chatMessages.scrollTop = this.chatMessages.scrollHeight;
        }
    }

    // 处理流式传输完成
    handleStreamComplete(assistantMessageElement, fullResponse, options = {}) {
        const historyOptions = {
            ...options,
            processSteps: options.processSteps || options.process_steps || this.snapshotCurrentProcessSteps()
        };
        if (assistantMessageElement) {
            assistantMessageElement.classList.remove('streaming');
            this.setAssistantMessageContent(assistantMessageElement, fullResponse, historyOptions);
        }
        // 保存流式消息到历史记录
        if (fullResponse) {
            this.currentChatHistory.push(this.buildHistoryMessage('assistant', fullResponse, historyOptions));
            // 如果当前对话是从历史记录加载的，更新历史记录
            if (this.isCurrentChatFromHistory) {
                this.updateCurrentChatHistory();
                this.renderChatHistory();
            }
        }
        this.resetCurrentProcessTracking();
    }

    // 显示通知
    showNotification(message, type = 'info') {
        // 创建通知元素
        const notification = document.createElement('div');
        notification.className = `notification ${type}`;
        notification.textContent = message;
        notification.style.cssText = `
            position: fixed;
            top: 20px;
            right: 20px;
            padding: 15px 20px;
            border-radius: 8px;
            color: white;
            font-weight: 500;
            z-index: 10000;
            animation: slideIn 0.3s ease;
            max-width: 300px;
        `;

        // 根据类型设置颜色（Google Material Design配色）
        const colors = {
            info: '#1a73e8',
            success: '#34a853',
            warning: '#fbbc04',
            error: '#ea4335'
        };
        notification.style.backgroundColor = colors[type] || colors.info;

        // 添加到页面
        document.body.appendChild(notification);

        // 3秒后自动移除
        setTimeout(() => {
            notification.style.animation = 'slideOut 0.3s ease';
            setTimeout(() => {
                if (notification.parentNode) {
                    notification.parentNode.removeChild(notification);
                }
            }, 300);
        }, 3000);
    }

    // 格式化文件大小
    formatFileSize(bytes) {
        if (bytes === 0) return '0 Bytes';
        const k = 1024;
        const sizes = ['Bytes', 'KB', 'MB', 'GB'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return Math.round(bytes / Math.pow(k, i) * 100) / 100 + ' ' + sizes[i];
    }

    // HTML转义
    escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    // 显示/隐藏加载遮罩层
    showLoadingOverlay(show) {
        if (this.loadingOverlay) {
            if (show) {
                this.loadingOverlay.style.display = 'flex';
                // 更新文字为通用处理中
                const loadingText = this.loadingOverlay.querySelector('.loading-text');
                const loadingSubtext = this.loadingOverlay.querySelector('.loading-subtext');
                if (loadingText) loadingText.textContent = '处理中，请稍候...';
                if (loadingSubtext) loadingSubtext.textContent = '后端正在处理，请耐心等待';
                // 防止页面滚动
                document.body.style.overflow = 'hidden';
            } else {
                this.loadingOverlay.style.display = 'none';
                // 恢复页面滚动
                document.body.style.overflow = '';
            }
        }
    }

}

// 添加CSS动画
const style = document.createElement('style');
style.textContent = `
    @keyframes slideIn {
        from {
            transform: translateX(100%);
            opacity: 0;
        }
        to {
            transform: translateX(0);
            opacity: 1;
        }
    }
    
    @keyframes slideOut {
        from {
            transform: translateX(0);
            opacity: 1;
        }
        to {
            transform: translateX(100%);
            opacity: 0;
        }
    }
`;
document.head.appendChild(style);

// 初始化应用
document.addEventListener('DOMContentLoaded', () => {
    new SuperBizAgentApp();
});
