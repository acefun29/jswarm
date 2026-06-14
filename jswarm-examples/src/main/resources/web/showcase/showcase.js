const AGENT_NAMES = {
  router: '路由专员',
  tech: '技术支持',
  sales: '销售专员',
  order: '订单专员',
  analyst: '数据分析师'
};

const EVENT_META = {
  onEnter: { label: '进场', icon: '→', inlineClass: 'onEnter' },
  onExit: { label: '离场', icon: '←', inlineClass: 'onExit' },
  handoff: { label: 'Handoff', icon: '⇄', inlineClass: 'handoff' },
  delegate: { label: 'Delegate', icon: '↪', inlineClass: 'delegate' },
  tool: { label: 'Tool', icon: '⚙', inlineClass: 'tool' },
  context: { label: 'Context', icon: '{ }', inlineClass: 'context' }
};

const USER_LABELS = {
  u001: '张三 · 黄金VIP',
  u002: '李四 · 普通用户'
};

const INLINE_TYPES = new Set(['onEnter', 'onExit', 'handoff', 'delegate', 'tool', 'context']);

const msgArea = document.getElementById('msg-area');
const timeline = document.getElementById('timeline');
const timelineEmpty = document.getElementById('timeline-empty');
const contextTable = document.getElementById('context-table');
const userInput = document.getElementById('user-input');
const sendBtn = document.getElementById('send-btn');
const agentTag = document.getElementById('agent-tag');
const userBadge = document.getElementById('user-badge');
const featureList = document.getElementById('feature-list');
const resetBtn = document.getElementById('reset-btn');
const clearTimelineBtn = document.getElementById('clear-timeline');
const topology = document.getElementById('topology');

let sessionId = localStorage.getItem('jswarm_showcase_session') || '';
let loading = false;
let lastUserId = 'u001';
let currentAgentId = 'router';

function clearStaleServiceWorkers() {
  if (!('serviceWorker' in navigator)) return;
  navigator.serviceWorker.getRegistrations().then(regs => {
    regs.forEach(reg => reg.unregister());
  }).catch(() => {});
}

function apiFetch(url, options) {
  return fetch(url, Object.assign({
    cache: 'no-store',
    headers: { 'Content-Type': 'application/json' }
  }, options || {}));
}

clearStaleServiceWorkers();

function selectedUserId() {
  const r = document.querySelector('input[name="user"]:checked');
  return r ? r.value : 'u001';
}

function persistSessionId(id) {
  sessionId = id || '';
  if (sessionId) {
    localStorage.setItem('jswarm_showcase_session', sessionId);
  } else {
    localStorage.removeItem('jswarm_showcase_session');
  }
}

function agentLabel(id) {
  if (!id) return 'router · 路由专员';
  return id + ' · ' + (AGENT_NAMES[id] || id);
}

function nowTime() {
  const d = new Date();
  return d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
}

function setLoading(on) {
  loading = on;
  userInput.disabled = on;
  sendBtn.disabled = on;
  resetBtn.disabled = on;
  document.querySelectorAll('.scene-btn, .scenario-btn').forEach(b => { b.disabled = on; });
}

function updateTopology(agentId) {
  currentAgentId = agentId || 'router';
  agentTag.textContent = agentLabel(currentAgentId);
  if (!topology) return;
  topology.querySelectorAll('.topo-node').forEach(node => {
    node.classList.toggle('active', node.dataset.agent === currentAgentId);
  });
}

function addMsg(text, role) {
  const el = document.createElement('div');
  el.className = 'msg ' + role;
  el.textContent = text;
  msgArea.appendChild(el);
  msgArea.scrollTop = msgArea.scrollHeight;
}

function buildInlineTrace(ev) {
  const meta = EVENT_META[ev.type] || { label: ev.type, icon: '·', inlineClass: ev.type };
  let title = meta.label;
  let detail = '';

  switch (ev.type) {
    case 'handoff':
      title = 'Handoff · ' + agentLabel(ev.from) + ' → ' + agentLabel(ev.to);
      break;
    case 'delegate':
      title = 'Delegate · ' + agentLabel(ev.from) + ' → ' + agentLabel(ev.to);
      detail = ev.task || '';
      break;
    case 'onEnter':
      title = 'onEnter · ' + agentLabel(ev.agent);
      detail = ev.detail || '';
      break;
    case 'onExit':
      title = 'onExit · ' + agentLabel(ev.agent);
      break;
    case 'tool':
      title = 'Tool · ' + agentLabel(ev.agent);
      detail = ev.detail || '';
      break;
    case 'context':
      title = 'Context 更新';
      detail = ev.detail || '';
      break;
    default:
      detail = ev.detail || '';
  }

  return { title, detail, meta };
}

function addInlineTrace(ev) {
  if (!INLINE_TYPES.has(ev.type)) return;

  const { title, detail, meta } = buildInlineTrace(ev);
  const el = document.createElement('div');
  el.className = 'msg trace-inline ' + (meta.inlineClass || ev.type);
  el.innerHTML =
    '<div class="trace-title">' + escapeHtml(title) + '</div>' +
    (detail ? '<div class="trace-detail">' + escapeHtml(detail) + '</div>' : '');
  msgArea.appendChild(el);
  msgArea.scrollTop = msgArea.scrollHeight;
}

function addLoading() {
  const el = document.createElement('div');
  el.className = 'msg agent';
  el.id = 'loading-msg';
  el.innerHTML = '<div class="skeleton"><span></span><span></span><span></span></div>';
  msgArea.appendChild(el);
  msgArea.scrollTop = msgArea.scrollHeight;
}

function removeLoading() {
  const el = document.getElementById('loading-msg');
  if (el) el.remove();
}

function formatEventBody(ev) {
  switch (ev.type) {
    case 'handoff':
      return agentLabel(ev.from) + ' → ' + agentLabel(ev.to);
    case 'delegate':
      return agentLabel(ev.from) + ' → ' + agentLabel(ev.to) +
        (ev.task ? ' · 任务: ' + ev.task : '');
    case 'onEnter':
    case 'onExit':
      return agentLabel(ev.agent) + (ev.detail ? ' · ' + ev.detail : '');
    case 'tool':
      return agentLabel(ev.agent) + ' · ' + (ev.detail || '—');
    case 'context':
      return ev.detail || '—';
    default:
      return ev.detail || '—';
  }
}

function renderEvents(events, options = { inline: true }) {
  if (!events || events.length === 0) return;
  if (timelineEmpty) timelineEmpty.style.display = 'none';

  events.forEach(ev => {
    if (options.inline) addInlineTrace(ev);
    if (ev.type === 'handoff' && ev.to) updateTopology(ev.to);
    if (ev.type === 'onEnter' && ev.agent) updateTopology(ev.agent);

    const meta = EVENT_META[ev.type] || { label: ev.type, icon: '·' };
    const el = document.createElement('div');
    el.className = 'event ' + (ev.type || '');
    el.innerHTML =
      '<div class="event-head">' +
        '<span class="event-icon">' + meta.icon + '</span>' +
        '<span class="type">' + meta.label + '</span>' +
        '<span class="event-time">' + nowTime() + '</span>' +
      '</div>' +
      '<div class="body">' + escapeHtml(formatEventBody(ev)) + '</div>';
    timeline.appendChild(el);
  });
  timeline.scrollTop = timeline.scrollHeight;
}

function renderContext(ctx) {
  if (!ctx || Object.keys(ctx).length === 0) {
    contextTable.innerHTML = '<div class="ctx-empty">暂无 Context 数据</div>';
    return;
  }
  const rows = Object.entries(ctx).map(([k, v]) =>
    '<div class="ctx-row"><span class="ctx-key">{' + escapeHtml(k) + '}</span>' +
    '<span class="ctx-val">' + escapeHtml(String(v)) + '</span></div>'
  ).join('');
  contextTable.innerHTML = rows;
}

function escapeHtml(s) {
  if (s == null) return '';
  const d = document.createElement('div');
  d.textContent = s;
  return d.innerHTML;
}

function updateUserBadge() {
  const uid = selectedUserId();
  userBadge.textContent = USER_LABELS[uid] || uid;
}

async function sendChat(message) {
  if (!message || loading) return;
  setLoading(true);
  addMsg(message, 'user');
  addLoading();
  try {
    const resp = await apiFetch('/api/chat', {
      method: 'POST',
      body: JSON.stringify({ sessionId, userId: selectedUserId(), message })
    });
    if (!resp.ok) {
      const err = await resp.json().catch(() => ({}));
      throw new Error(err.error || ('HTTP ' + resp.status));
    }
    const data = await resp.json();
    removeLoading();
    if (data.error) {
      addMsg('错误: ' + data.error, 'system');
    } else {
      persistSessionId(data.sessionId);
      updateTopology(data.currentAgent);
      renderEvents(data.events);
      renderContext(data.context);
      addMsg(data.reply, 'agent');
    }
  } catch (e) {
    removeLoading();
    addMsg('连接失败: ' + e.message, 'system');
  }
  setLoading(false);
  userInput.focus();
}

async function runScenario(scenarioId) {
  if (loading) return;
  setLoading(true);
  addMsg('[单轮 SwarmRunner] 场景: ' + scenarioId, 'system');
  addLoading();
  try {
    const resp = await apiFetch('/api/scenario/' + scenarioId, {
      method: 'POST',
      body: JSON.stringify({ userId: selectedUserId() })
    });
    if (!resp.ok) {
      const err = await resp.json().catch(() => ({}));
      throw new Error(err.error || ('HTTP ' + resp.status));
    }
    const data = await resp.json();
    removeLoading();
    if (data.error) {
      addMsg('错误: ' + data.error, 'system');
    } else {
      renderEvents(data.events);
      renderContext(data.context);
      addMsg(data.reply, 'agent');
    }
  } catch (e) {
    removeLoading();
    addMsg('连接失败: ' + e.message, 'system');
  }
  setLoading(false);
}

async function loadFeatures() {
  try {
    const resp = await fetch('/api/features', { cache: 'no-store' });
    const data = await resp.json();
    featureList.innerHTML = '';
    (data.features || []).forEach(f => {
      const li = document.createElement('li');
      li.textContent = f.label;
      li.title = f.agent || '';
      featureList.appendChild(li);
    });
  } catch (_) {
    featureList.innerHTML = '<li>加载失败</li>';
  }
}

async function resetSession(clearChat) {
  await apiFetch('/api/reset', {
    method: 'POST',
    body: JSON.stringify({ sessionId, userId: selectedUserId() })
  });
  persistSessionId('');
  timeline.innerHTML = '';
  if (timelineEmpty) {
    timeline.appendChild(timelineEmpty);
    timelineEmpty.style.display = 'block';
  }
  renderContext({});
  updateTopology('router');
  if (clearChat !== false) {
    msgArea.innerHTML = '';
    addMsg('会话已重置，SwarmContext 已按当前用户重新注入', 'system');
  }
  lastUserId = selectedUserId();
  updateUserBadge();
}

function onUserChange() {
  const uid = selectedUserId();
  if (uid !== lastUserId) {
    resetSession(false).then(() => {
      addMsg('已切换为 ' + (USER_LABELS[uid] || uid) + '，会话与 Context 已重置', 'system');
    });
  }
}

function useSceneMessage(msg, autoSend) {
  userInput.value = msg;
  userInput.focus();
  if (autoSend) {
    userInput.value = '';
    sendChat(msg);
  }
}

sendBtn.addEventListener('click', () => {
  const t = userInput.value.trim();
  if (t) { userInput.value = ''; sendChat(t); }
});

userInput.addEventListener('keydown', e => {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault();
    const t = userInput.value.trim();
    if (t) { userInput.value = ''; sendChat(t); }
  }
});

document.querySelectorAll('.scene-btn').forEach(btn => {
  btn.addEventListener('click', () => {
    useSceneMessage(btn.dataset.msg, btn.dataset.send === 'true');
  });
});

document.querySelectorAll('.scenario-btn').forEach(btn => {
  btn.addEventListener('click', () => runScenario(btn.dataset.scenario));
});

document.querySelectorAll('input[name="user"]').forEach(r => {
  r.addEventListener('change', onUserChange);
});

resetBtn.addEventListener('click', () => resetSession(true));

clearTimelineBtn.addEventListener('click', () => {
  timeline.innerHTML = '';
  if (timelineEmpty) {
    timeline.appendChild(timelineEmpty);
    timelineEmpty.style.display = 'block';
  }
});

document.querySelectorAll('.mobile-tabs .tab').forEach(tab => {
  tab.addEventListener('click', () => {
    document.querySelectorAll('.mobile-tabs .tab').forEach(t => t.classList.remove('active'));
    document.querySelectorAll('.panel').forEach(p => p.classList.remove('active'));
    tab.classList.add('active');
    const panel = document.querySelector('.panel[data-panel="' + tab.dataset.panel + '"]');
    if (panel) panel.classList.add('active');
  });
});

loadFeatures();
updateUserBadge();
updateTopology('router');
renderContext({});
