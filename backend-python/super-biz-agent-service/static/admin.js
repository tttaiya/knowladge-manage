const tokenStore = {
    get access() { return localStorage.getItem('access_token'); },
    get refresh() { return localStorage.getItem('refresh_token'); },
    set(pair) {
        localStorage.setItem('access_token', pair.access_token);
        localStorage.setItem('refresh_token', pair.refresh_token);
    },
    clear() {
        localStorage.removeItem('access_token');
        localStorage.removeItem('refresh_token');
    }
};

async function apiFetch(url, options = {}, retry = true) {
    const headers = new Headers(options.headers || {});
    if (tokenStore.access) headers.set('Authorization', `Bearer ${tokenStore.access}`);
    if (!headers.has('Content-Type') && !(options.body instanceof FormData)) headers.set('Content-Type', 'application/json');
    const response = await fetch(url, { ...options, headers });
    if (response.status === 401 && retry && tokenStore.refresh) {
        const refreshed = await fetch('/api/auth/refresh', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ refresh_token: tokenStore.refresh })
        });
        if (refreshed.ok) {
            tokenStore.set(await refreshed.json());
            return apiFetch(url, options, false);
        }
    }
    if (response.status === 401) {
        tokenStore.clear();
        location.href = '/';
    }
    return response;
}

const state = { settings: [] };

const modelSettingKeys = [
    'llm.base_url',
    'llm.api_key',
    'llm.chat_model',
    'llm.timeout_seconds'
];

function setting(key) {
    return state.settings.find(item => item.key === key);
}

function escapeHtml(value) {
    const div = document.createElement('div');
    div.textContent = value ?? '';
    return div.innerHTML;
}

async function saveSetting(key, value) {
    await apiFetch(`/api/admin/settings/${key}`, {
        method: 'PATCH',
        body: JSON.stringify({ value })
    });
}

async function loadMe() {
    const res = await apiFetch('/api/auth/me');
    if (!res.ok) return;
    const me = await res.json();
    document.getElementById('adminUser').textContent = `${me.display_name || me.username}`;
}

async function loadDashboard() {
    const [summaryRes, trendRes] = await Promise.all([
        apiFetch('/api/admin/dashboard/summary'),
        apiFetch('/api/admin/dashboard/qa-trend')
    ]);
    const summary = await summaryRes.json();
    const trend = await trendRes.json();
    document.getElementById('dashboardPanel').innerHTML = `
        <h2>运营总览</h2>
        <div class="metric-grid">
            <div class="metric-card"><span>用户数</span><strong>${summary.user_count}</strong></div>
            <div class="metric-card"><span>知识库</span><strong>${summary.knowledge_base_count}</strong></div>
            <div class="metric-card"><span>文档</span><strong>${summary.document_count}</strong></div>
            <div class="metric-card"><span>切片</span><strong>${summary.chunk_count}</strong></div>
            <div class="metric-card"><span>问答</span><strong>${summary.qa_count}</strong></div>
        </div>
        <section class="admin-section">
            <h3>近 30 天知识问答日活趋势</h3>
            <p class="dashboard-hint">直观查看业务使用量波动</p>
            ${renderTrendBars(trend.items || [])}
        </section>
    `;
}

function renderTrendBars(items) {
    if (!items.length) {
        return '<p class="empty-hint">暂无趋势数据。</p>';
    }
    const maxCount = Math.max(1, ...items.map(item => Number(item.qa_count) || 0));
    return `
        <div class="trend-bar-chart">
            ${items.map(item => {
                const count = Number(item.qa_count) || 0;
                const height = count ? Math.max(8, Math.round((count / maxCount) * 120)) : 4;
                const label = String(item.date || '').slice(5);
                return `
                    <div class="trend-bar-item" title="${escapeHtml(item.date)}：${count} 次问答">
                        <div class="trend-bar-value">${count}</div>
                        <div class="trend-bar-track">
                            <div class="trend-bar" style="height: ${height}px"></div>
                        </div>
                        <div class="trend-bar-label">${escapeHtml(label)}</div>
                    </div>
                `;
            }).join('')}
        </div>
    `;
}

async function loadSettings() {
    const res = await apiFetch('/api/admin/settings');
    state.settings = await res.json();
}

function renderInput(item, options = {}) {
    const type = options.type || (item.value_type === 'int' || item.value_type === 'float' ? 'number' : 'text');
    const value = item.is_secret && item.value === '******' ? '' : item.value;
    const attrs = [
        `data-setting="${item.key}"`,
        `value="${escapeHtml(value)}"`,
        type === 'number' ? 'step="any"' : '',
        options.placeholder ? `placeholder="${escapeHtml(options.placeholder)}"` : '',
        item.is_secret ? 'type="password"' : `type="${type}"`
    ].filter(Boolean).join(' ');
    return `<input ${attrs}>`;
}

function renderModelPanel() {
    const rows = modelSettingKeys.map(key => setting(key)).filter(Boolean);
    document.getElementById('modelPanel').innerHTML = `
        <h2>模型设置</h2>
        <div class="admin-form-grid">
            ${rows.map(item => `
                <label class="setting-row">
                    <span>${item.description}<small>${item.key}</small></span>
                    ${renderInput(item, { placeholder: item.is_secret ? '留空则不修改' : '' })}
                </label>
            `).join('')}
        </div>
        <div class="admin-actions">
            <button id="saveModelSettingsBtn">保存模型设置</button>
        </div>
    `;
    document.getElementById('saveModelSettingsBtn').addEventListener('click', async () => {
        for (const item of rows) {
            const input = document.querySelector(`[data-setting="${CSS.escape(item.key)}"]`);
            if (item.is_secret && !input.value) continue;
            await saveSetting(item.key, input.value);
        }
        await reloadAdminData();
        renderModelPanel();
    });
}

async function reloadAdminData() {
    await loadSettings();
}

function showTab(tab) {
    document.querySelectorAll('.admin-panel').forEach(panel => panel.style.display = 'none');
    if (tab === 'dashboard') document.getElementById('dashboardPanel').style.display = 'block';
    if (tab === 'models') {
        renderModelPanel();
        document.getElementById('modelPanel').style.display = 'block';
    }
}

document.addEventListener('DOMContentLoaded', async () => {
    document.querySelectorAll('[data-admin-tab]').forEach(btn => btn.addEventListener('click', () => showTab(btn.dataset.adminTab)));
    await loadMe();
    await Promise.all([loadDashboard(), reloadAdminData()]);
    showTab('dashboard');
});
