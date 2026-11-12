// TrustDesk demo UI. Plain JavaScript, no build step; served by the Spring Boot backend.
//
// Security: ticket text, documents and AI replies are untrusted. Everything from the server is
// inserted with textContent (via el()), never parsed as HTML, so it can never run as markup or script.
'use strict';

const state = {
  token: sessionStorage.getItem('trustdesk-token') || '',
  tickets: [],
  ticket: null,          // the selected ticket (detail)
  evalPoll: null,
  evalRuns: [],          // all eval runs, newest first (for the run picker)
};

// ---------- small helpers ----------

/** Creates an element. attrs: {class, text, onclick, ...}; children: nodes or strings (as text). */
function el(tag, attrs = {}, ...children) {
  const node = document.createElement(tag);
  for (const [key, value] of Object.entries(attrs)) {
    if (value === undefined || value === null || value === false) continue;
    if (key === 'class') node.className = value;
    else if (key === 'text') node.textContent = value;
    else if (key.startsWith('on')) node.addEventListener(key.slice(2), value);
    else node.setAttribute(key, value === true ? '' : value);
  }
  for (const child of children.flat()) {
    if (child === null || child === undefined || child === false) continue;
    node.append(child instanceof Node ? child : document.createTextNode(String(child)));
  }
  return node;
}

/** Replaces a node's children. Like el(), it flattens lists and skips null/undefined/false. */
function setChildren(node, ...children) {
  node.replaceChildren(...children.flat(Infinity).filter((c) => c !== null && c !== undefined && c !== false));
}

function badge(text, color) {
  return el('span', { class: 'badge ' + (color || ''), text });
}

function statusColor(status) {
  return {
    generated: 'green', executed: 'green', completed: 'green', approved: 'blue', passed: 'green',
    needs_edit: 'amber', approval_required: 'amber', running: 'blue',
    escalated: 'red', rejected: 'red', failed: 'red', not_passed: 'amber',
  }[status] || '';
}

function kv(pairs) {
  const dl = el('dl', { class: 'kv' });
  for (const [k, v] of pairs) {
    dl.append(el('dt', { text: k }), el('dd', {}, v === null || v === undefined || v === '' ? '—' : v));
  }
  return dl;
}

function formatTime(iso) {
  if (!iso) return '—';
  return iso.replace('T', ' ').replace(/\.\d+/, '').replace('+05:30', ' IST');
}

function pct(value) {
  return value === null || value === undefined ? '—' : Math.round(value * 1000) / 10 + '%';
}

function toast(message, isError) {
  const box = document.getElementById('toast');
  box.textContent = message;
  box.className = 'toast' + (isError ? ' error' : '');
  clearTimeout(toast.timer);
  toast.timer = setTimeout(() => box.classList.add('hidden'), isError ? 7000 : 3000);
}

/** Calls the API with the demo token. Returns the parsed body, or throws an Error with the API message. */
async function api(method, path, body) {
  const headers = { Authorization: 'Bearer ' + state.token };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  const response = await fetch(path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
  const text = await response.text();
  const data = text ? JSON.parse(text) : null;
  if (!response.ok) {
    const error = new Error((data && data.message) || 'HTTP ' + response.status);
    error.status = response.status;
    throw error;
  }
  return data;
}

/** Runs an async action with the button disabled and errors shown as a toast. */
async function withBusy(button, label, action) {
  const original = button.textContent;
  button.disabled = true;
  button.textContent = label;
  try {
    return await action();
  } catch (e) {
    toast(e.message, true);
    if (e.status === 401) document.getElementById('token-input').focus();
    return undefined;
  } finally {
    button.disabled = false;
    button.textContent = original;
  }
}

// ---------- token and tabs ----------

/** Reads the URL fragment, e.g. #ticket=tkt_9001 or #view=evals (the fragment is never sent to the server). */
function hashParams() {
  return new URLSearchParams(location.hash.slice(1));
}

function initToken() {
  // Optional convenience for demos: /#token=<token>. It is removed from the address bar immediately.
  const params = hashParams();
  if (params.has('token')) {
    state.token = params.get('token');
    sessionStorage.setItem('trustdesk-token', state.token);
    params.delete('token');
    history.replaceState(null, '', location.pathname + (params.toString() ? '#' + params : ''));
  }
  const input = document.getElementById('token-input');
  input.value = state.token;
  document.getElementById('token-form').addEventListener('submit', (event) => {
    event.preventDefault();
    state.token = input.value.trim();
    sessionStorage.setItem('trustdesk-token', state.token);
    toast('Token saved for this browser tab.');
    loadTickets();
  });
}

function showView(view) {
  document.querySelectorAll('.tab').forEach((t) => t.classList.toggle('active', t.dataset.view === view));
  document.getElementById('view-tickets').classList.toggle('hidden', view !== 'tickets');
  document.getElementById('view-evals').classList.toggle('hidden', view !== 'evals');
  if (view === 'evals') loadLatestEval();
}

function initTabs() {
  for (const tab of document.querySelectorAll('.tab')) {
    tab.addEventListener('click', () => {
      history.replaceState(null, '', location.pathname + (tab.dataset.view === 'evals' ? '#view=evals' : ''));
      showView(tab.dataset.view);
    });
  }
}

// ---------- ticket queue ----------

async function loadTickets() {
  const list = document.getElementById('ticket-list');
  if (!state.token) {
    setChildren(list, el('li', { class: 'muted', text: 'Enter the demo token above to load tickets.' }));
    return;
  }
  try {
    state.tickets = await api('GET', '/tickets');
  } catch (e) {
    setChildren(list, el('li', { class: 'muted', text: e.message }));
    return;
  }
  setChildren(list, ...state.tickets.map((t) => el('li', {
    'data-id': t.ticket_id,
    class: state.ticket && state.ticket.ticket_id === t.ticket_id ? 'selected' : '',
    onclick: () => openTicket(t.ticket_id),
  },
  el('div', { class: 't-id', text: t.ticket_id + ' · ' + t.channel }),
  el('div', { class: 't-subject', text: t.subject }),
  el('div', { class: 't-meta', text: t.customer_name + ' · ' + formatTime(t.created_at) }))));
}

// ---------- ticket detail ----------

async function openTicket(ticketId) {
  history.replaceState(null, '', location.pathname + '#ticket=' + encodeURIComponent(ticketId));
  document.querySelectorAll('#ticket-list li').forEach((li) => li.classList.toggle('selected', li.dataset.id === ticketId));
  const detail = document.getElementById('ticket-detail');
  setChildren(detail, el('p', { class: 'empty', text: 'Loading ' + ticketId + '…' }));
  try {
    state.ticket = await api('GET', '/tickets/' + ticketId);
  } catch (e) {
    setChildren(detail, el('p', { class: 'empty', text: e.message }));
    return;
  }
  const triageCard = el('div', { class: 'card', id: 'triage-card' });
  const draftCard = el('div', { class: 'card', id: 'draft-card' });
  const actionsCard = el('div', { class: 'card', id: 'actions-card' });
  const tracesCard = el('div', { class: 'card', id: 'traces-card' });
  setChildren(detail, ticketCard(state.ticket), triageCard, draftCard, actionsCard, tracesCard);
  await Promise.all([loadTriage(), loadDraft(), loadActions(), loadTraces()]);
}

function ticketCard(t) {
  const c = t.customer;
  const o = t.order;
  return el('div', { class: 'card' },
    el('div', { class: 'card-head' },
      el('h3', { text: t.subject }),
      el('div', { class: 'row' }, badge(t.ticket_id), badge(t.channel), badge(t.status))),
    el('div', { class: 'body-text', text: t.body }),
    el('div', { class: 'grid' },
      el('div', {}, el('div', { class: 'section-label', text: 'Customer' }), kv([
        ['Name', c.name],
        ['Tier', c.tier],
        ['Identity verified', c.verified ? badge('verified', 'green') : badge('not verified', 'amber')],
        ['Email', c.email],
      ])),
      el('div', {}, el('div', { class: 'section-label', text: 'Order' }), o ? kv([
        ['Order', o.order_id + ' (' + o.status + ')'],
        ['Delivered', o.delivered_at],
        ['Items', o.items.map((i) => i.name + (i.final_sale ? ' (final sale)' : '')).join(', ')],
        ['Total', o.total + ' ' + o.currency],
      ]) : el('p', { class: 'muted', text: 'No linked order.' })),
      el('div', {}, el('div', { class: 'section-label', text: 'Ticket' }), kv([
        ['Created', formatTime(t.created_at)],
        ['Customer ID', c.customer_id],
      ]))));
}

function guardrailBlock(guardrail) {
  if (!guardrail) return null;
  if (guardrail.status === 'passed') return el('div', { class: 'row' }, el('span', { class: 'muted', text: 'Guardrails:' }), badge('passed', 'green'));
  return el('div', {},
    el('div', { class: 'row' }, el('span', { class: 'muted', text: 'Guardrails:' }), badge('flagged', 'red')),
    el('ul', { class: 'list' }, guardrail.findings.map((f) =>
      el('li', {}, badge(f.type, f.type === 'placeholder_fixed' ? '' : 'red'), ' ', el('span', { class: 'muted', text: f.source + ': ' }), f.detail))));
}

function traceLink(runId) {
  if (!runId) return null;
  return el('button', { class: 'ghost', type: 'button', onclick: () => showTrace(runId) }, 'View trace');
}

// --- triage ---

async function loadTriage(fresh) {
  const card = document.getElementById('triage-card');
  const id = state.ticket.ticket_id;
  const runButton = el('button', { type: 'button' }, fresh === undefined ? 'Run triage' : 'Run triage again');
  runButton.addEventListener('click', () => withBusy(runButton, 'Triaging…', async () => {
    const result = await api('POST', '/tickets/' + id + '/triage');
    renderTriage(card, result, runButton);
    loadTraces();
    toast('Triage stored.');
  }));
  if (fresh) return renderTriage(card, fresh, runButton);
  try {
    renderTriage(card, await api('GET', '/tickets/' + id + '/triage'), runButton);
  } catch (e) {
    setChildren(card, el('div', { class: 'card-head' }, el('h3', { text: 'Triage' }), runButton),
      el('p', { class: 'muted', text: e.status === 404 ? 'Not triaged yet.' : e.message }));
  }
}

function renderTriage(card, t, runButton) {
  runButton.textContent = 'Run triage again';
  setChildren(card,
    el('div', { class: 'card-head' }, el('h3', { text: 'Triage' }), el('div', { class: 'row' }, traceLink(t.run_id), runButton)),
    el('div', { class: 'row' },
      badge(t.category, 'blue'), badge('priority: ' + t.priority, t.priority === 'urgent' ? 'red' : t.priority === 'high' ? 'amber' : ''),
      badge('sentiment: ' + t.sentiment),
      t.should_escalate ? badge('escalate to human', 'red') : badge('no escalation', 'green')),
    el('p', { text: t.reason_summary }),
    kv([
      ['Policies shown to AI', t.retrieved_doc_ids.join(', ')],
      ['Provider', t.provider + ' · ' + t.model],
      ['Created', formatTime(t.created_at)],
    ]),
    guardrailBlock(t.guardrail));
}

// --- draft ---

async function loadDraft() {
  const card = document.getElementById('draft-card');
  const id = state.ticket.ticket_id;
  const runButton = el('button', { type: 'button' }, 'Generate draft');
  runButton.addEventListener('click', () => withBusy(runButton, 'Drafting…', async () => {
    const draft = await api('POST', '/tickets/' + id + '/draft-reply');
    renderDraft(card, draft, runButton);
    loadTriage();
    loadTraces();
    toast('Draft stored (status: ' + draft.status + ').');
  }));
  try {
    renderDraft(card, await api('GET', '/tickets/' + id + '/draft-reply'), runButton);
  } catch (e) {
    setChildren(card, el('div', { class: 'card-head' }, el('h3', { text: 'Draft reply' }), runButton),
      el('p', { class: 'muted', text: e.status === 404 ? 'No draft yet. Generating one also triages the ticket if needed.' : e.message }));
  }
}

function renderDraft(card, d, runButton) {
  runButton.textContent = 'Generate new draft';
  const facts = d.policy_facts;
  setChildren(card,
    el('div', { class: 'card-head' },
      el('div', { class: 'row' }, el('h3', { text: 'Draft reply' }), badge(d.status, statusColor(d.status))),
      el('div', { class: 'row' }, traceLink(d.run_id), runButton)),
    d.escalation_reason ? el('p', {}, badge('Escalation', 'red'), ' ', d.escalation_reason) : null,
    d.status === 'needs_edit' ? el('p', {}, badge('Needs edit', 'amber'), ' A reviewer must fix the wording before this reply is sent (see guardrail findings).') : null,
    el('div', { class: 'body-text', text: d.body }),
    el('div', { class: 'row' }, el('span', { class: 'muted', text: 'Citations:' }),
      d.citations.length ? d.citations.map((c) => badge(c, 'blue chip')) : el('span', { class: 'muted', text: 'none' })),
    el('div', { class: 'section-label', text: 'Recommended actions' }),
    d.recommended_actions.length
      ? d.recommended_actions.map((a) => recommendedAction(a, d))
      : el('p', { class: 'muted', text: 'No action recommended.' }),
    facts ? el('div', {}, el('div', { class: 'section-label', text: 'Policy facts (as of ticket date ' + facts.as_of_date + ')' }),
      kv([
        ['Return window', facts.return_window + (facts.return_window_ends_on ? ' (last day ' + facts.return_window_ends_on + ')' : '')],
        ...facts.items.map((i) => [i.name, (i.returnable ? 'returnable' : 'not returnable: ' + i.not_returnable_reason) + '; warranty ' + i.warranty + ' – ' + i.warranty_note]),
      ])) : null,
    d.validation_notes.length ? el('div', {}, el('div', { class: 'section-label', text: 'Removed by validation' }),
      el('ul', { class: 'list' }, d.validation_notes.map((n) => el('li', { text: n })))) : null,
    el('div', { class: 'section-label', text: 'Safety' }),
    guardrailBlock(d.guardrail),
    el('p', { class: 'muted', text: 'Policies shown to AI: ' + d.retrieved_doc_ids.join(', ') + ' · ' + d.provider + ' · ' + d.model }));
}

function recommendedAction(action, draft) {
  const button = el('button', { type: 'button' }, 'Request action');
  button.addEventListener('click', () => withBusy(button, 'Requesting…', async () => {
    const payload = buildPayload(action, draft);
    const result = await api('POST', '/tool-actions', { ticket_id: state.ticket.ticket_id, tool_name: action.tool_name, payload });
    toast(result.idempotent_replay ? 'Already requested with this idempotency key: existing action returned.' : 'Action requested: ' + result.status + '.');
    loadActions();
  }));
  return el('div', { class: 'action' },
    el('div', { class: 'action-head' },
      el('div', { class: 'row' }, el('strong', { text: action.tool_name }),
        action.requires_human_approval ? badge('requires human approval', 'amber') : badge('no approval needed', 'green')),
      button),
    el('div', { class: 'muted', text: action.reason }));
}

/** Fills the tool's required fields from the ticket. The idempotency key is fixed per draft, so clicking twice cannot create two actions. */
function buildPayload(action, draft) {
  const t = state.ticket;
  const order = t.order || {};
  const firstItem = (order.items || [])[0] || {};
  const base = { reason: action.reason || 'Recommended in draft ' + draft.draft_id };
  const byTool = {
    create_replacement_order: { order_id: order.order_id, sku: firstItem.sku },
    start_refund_review: { order_id: order.order_id, amount: order.total },
    open_carrier_investigation: { order_id: order.order_id, tracking_number: order.tracking_number },
    escalate_to_human: { ticket_id: t.ticket_id, queue: 'specialist' },
    issue_coupon: { customer_id: t.customer.customer_id, amount: 500 },
    lock_account: { customer_id: t.customer.customer_id },
  };
  return Object.assign(base, byTool[action.tool_name] || {}, {
    idempotency_key: t.ticket_id + '-' + action.tool_name + '-' + draft.draft_id.slice(-8),
  });
}

// --- tool actions ---

async function loadActions() {
  const card = document.getElementById('actions-card');
  let actions;
  try {
    actions = await api('GET', '/tickets/' + state.ticket.ticket_id + '/tool-actions');
  } catch (e) {
    setChildren(card, el('h3', { text: 'Tool actions' }), el('p', { class: 'muted', text: e.message }));
    return;
  }
  setChildren(card,
    el('div', { class: 'card-head' }, el('h3', { text: 'Tool actions' }),
      el('span', { class: 'muted', text: 'Sensitive actions need a human approval before they can run.' })),
    actions.length ? actions.map(actionCard) : el('p', { class: 'muted', text: 'No actions requested yet. Use "Request action" on a draft.' }));
}

function actionCard(a) {
  const reviewer = el('input', { placeholder: 'Reviewer ID (e.g. manager_priya)', 'aria-label': 'Reviewer ID' });
  const reason = el('input', { placeholder: 'Reason', 'aria-label': 'Reason' });
  const decide = (verb, button) => withBusy(button, verb === 'approve' ? 'Approving…' : 'Rejecting…', async () => {
    await api('POST', '/tool-actions/' + a.action_id + '/' + verb, { reviewer_id: reviewer.value.trim(), reason: reason.value.trim() });
    toast('Action ' + (verb === 'approve' ? 'approved' : 'rejected') + '.');
    loadActions();
  });
  const approve = el('button', { type: 'button' }, 'Approve');
  approve.addEventListener('click', () => decide('approve', approve));
  const reject = el('button', { type: 'button', class: 'danger' }, 'Reject');
  reject.addEventListener('click', () => decide('reject', reject));
  const execute = el('button', { type: 'button' }, 'Execute');
  execute.addEventListener('click', () => withBusy(execute, 'Executing…', async () => {
    const result = await api('POST', '/tool-actions/' + a.action_id + '/execute');
    toast(result.idempotent_replay ? 'Already executed: stored result returned, tool not run again.' : 'Executed.');
    loadActions();
  }));

  const canDecide = a.status === 'approval_required';
  const canExecute = a.status === 'approval_required' || a.status === 'approved' || a.status === 'executed';
  return el('div', { class: 'action' },
    el('div', { class: 'action-head' },
      el('div', { class: 'row' }, el('strong', { text: a.tool_name }), badge(a.status, statusColor(a.status)),
        a.requires_human_approval ? badge('sensitive tool') : null, badge('risk: ' + a.risk_level)),
      // Execute stays visible before approval on purpose: the demo shows the server refusing it (409).
      canExecute ? execute : null),
    kv([
      ['Action ID', a.action_id],
      ['Idempotency key', a.idempotency_key],
      ['Payload', Object.entries(a.payload).filter(([k]) => k !== 'idempotency_key').map(([k, v]) => k + '=' + v).join(', ')],
      ['Requested by', a.requested_by + ' · ' + formatTime(a.created_at)],
      ['Approvals', a.approvals.length ? a.approvals.map((p) => p.decision + ' by ' + p.reviewer_id + ': ' + p.reason).join('; ') : 'none'],
      ['Result', a.result ? Object.entries(a.result).map(([k, v]) => k + '=' + v).join(', ') : (a.failure_reason || '—')],
    ]),
    canDecide ? el('div', { class: 'review' }, reviewer, reason, approve, reject) : null);
}

// --- traces ---

async function loadTraces() {
  const card = document.getElementById('traces-card');
  let runs;
  try {
    runs = await api('GET', '/tickets/' + state.ticket.ticket_id + '/agent-runs');
  } catch (e) {
    setChildren(card, el('h3', { text: 'AI runs (traces)' }), el('p', { class: 'muted', text: e.message }));
    return;
  }
  setChildren(card,
    el('div', { class: 'card-head' }, el('h3', { text: 'AI runs (traces)' }), el('span', { class: 'muted', text: runs.length + ' runs, newest first' })),
    runs.length ? runs.slice().reverse().slice(0, 12).map(traceItem) : el('p', { class: 'muted', text: 'No AI runs yet.' }));
}

function traceItem(run) {
  return el('details', { class: 'trace', id: 'trace-' + run.run_id },
    el('summary', {},
      badge(run.run_type, 'blue'), badge(run.status, statusColor(run.status)),
      el('span', { class: 'muted', text: formatTime(run.created_at) + ' · ' + run.latency_ms + ' ms · ' + run.model_provider })),
    traceBody(run));
}

function traceBody(run) {
  return el('div', {},
    kv([
      ['Run ID', run.run_id],
      ['Result ID', run.result_id],
      ['Documents', run.retrieved_doc_ids.join(', ')],
      ['Tool calls', run.tool_calls.length ? run.tool_calls.map((c) => c.tool_name + ' (' + c.outcome + ')').join(', ') : 'none'],
      ['Model', run.model_provider + ' · ' + run.model_name + ' · ' + run.prompt_version],
      ['Error', run.error],
    ]),
    run.tool_calls.filter((c) => c.outcome === 'removed').length
      ? el('ul', { class: 'list' }, run.tool_calls.filter((c) => c.outcome === 'removed').map((c) => el('li', { text: c.tool_name + ': ' + c.reason })))
      : null,
    guardrailBlock(run.guardrail_results));
}

async function showTrace(runId) {
  await loadTraces();
  const item = document.getElementById('trace-' + runId);
  if (item) {
    item.open = true;
    item.scrollIntoView({ behavior: 'smooth', block: 'center' });
  } else {
    toast('Trace ' + runId + ' is older than the runs shown.', true);
  }
}

// ---------- evaluations ----------

async function loadLatestEval() {
  const box = document.getElementById('eval-detail');
  if (!state.token) {
    setChildren(box, el('p', { class: 'muted', text: 'Enter the demo token to see evaluations.' }));
    return;
  }
  try {
    const runs = await api('GET', '/eval-runs');
    if (!runs.length) {
      setChildren(box, el('p', { class: 'muted', text: 'No evaluation runs yet.' }));
      return;
    }
    state.evalRuns = runs;
    renderEval(runs[0]);
    if (runs[0].status === 'running') pollEval(runs[0].eval_run_id);
  } catch (e) {
    setChildren(box, el('p', { class: 'muted', text: e.message }));
  }
}

function pollEval(evalRunId) {
  clearInterval(state.evalPoll);
  state.evalPoll = setInterval(async () => {
    try {
      const run = await api('GET', '/eval-runs/' + evalRunId);
      state.evalRuns = [run, ...(state.evalRuns || []).filter((r) => r.eval_run_id !== run.eval_run_id)];
      renderEval(run);
      if (run.status !== 'running') {
        clearInterval(state.evalPoll);
        toast('Evaluation ' + run.status + ': ' + run.passed_cases + '/' + run.total_cases + ' cases passed.');
      }
    } catch (e) {
      clearInterval(state.evalPoll);
      toast(e.message, true);
    }
  }, 3000);
}

/** A small table of all eval runs; clicking one shows its report. */
function runPicker(selectedId) {
  const runs = state.evalRuns || [];
  if (runs.length < 2) return null;
  return el('details', { class: 'card run-picker' },
    el('summary', { text: 'All evaluation runs (' + runs.length + ') – click one to show it' }),
    el('div', { class: 'table-wrap' }, el('table', {},
      el('thead', {}, el('tr', {}, ['Queued', 'Status', 'Passed', 'Failed AI calls', 'Model', 'Prompt version'].map((h) => el('th', { text: h })))),
      el('tbody', {}, runs.map((r) => el('tr', {
        class: r.eval_run_id === selectedId ? 'selected-row' : '',
        style: 'cursor: pointer',
        onclick: () => renderEval(r),
      },
      el('td', { text: formatTime(r.queued_at) }),
      el('td', {}, badge(r.status, statusColor(r.status))),
      el('td', { text: r.passed_cases + ' / ' + r.total_cases }),
      el('td', { text: String(r.case_results.filter((c) => c.error).length) }),
      el('td', { text: r.model_provider + ' · ' + r.model_name }),
      el('td', { text: r.prompt_version })))))));
}

function renderEval(run) {
  const metrics = [
    ['Cases passed', run.passed_cases + ' / ' + run.total_cases],
    ['Category accuracy', pct(run.category_accuracy)],
    ['Priority accuracy', pct(run.priority_accuracy)],
    ['Citation coverage', pct(run.citation_coverage)],
    ['Unsafe actions blocked', pct(run.unsafe_action_block_rate)],
    ['Allowed action recall', pct(run.allowed_action_recall)],
    ['Escalation accuracy', pct(run.escalation_accuracy)],
    ['Answer requirements', pct(run.answer_requirement_coverage)],
  ];
  const failedCalls = run.case_results.filter((c) => c.error).length;
  setChildren(document.getElementById('eval-detail'),
    runPicker(run.eval_run_id),
    el('div', { class: 'card' },
      el('div', { class: 'card-head' },
        el('div', { class: 'row' }, el('h3', { text: run === (state.evalRuns || [])[0] ? 'Latest run' : 'Selected run' }), badge(run.status, statusColor(run.status))),
        el('span', { class: 'muted', text: run.model_provider + ' · ' + run.model_name + ' · ' + run.prompt_version })),
      el('p', { class: 'muted', text: run.eval_run_id + ' · started ' + formatTime(run.started_at || run.queued_at)
        + (run.finished_at ? ' · finished ' + formatTime(run.finished_at) : ' · ' + run.completed_cases + '/' + run.total_cases + ' cases done') }),
      run.error ? el('p', {}, badge('error', 'red'), ' ', run.error) : null,
      failedCalls ? el('p', {}, badge(failedCalls + ' AI calls failed', 'amber'),
        ' These cases count as failed (e.g. the AI provider\'s rate limit), not as wrong answers.') : null,
      el('div', { class: 'metrics' }, metrics.map(([label, value]) =>
        el('div', { class: 'metric' }, el('div', { class: 'value', text: value }), el('div', { class: 'label', text: label })))),
      run.adversarial_checks.length ? el('div', {},
        el('div', { class: 'section-label', text: 'Adversarial cases' }),
        el('div', { class: 'table-wrap' }, el('table', {},
          el('thead', {}, el('tr', {}, ['Case', 'Description', 'Verdict', 'Summary'].map((h) => el('th', { text: h })))),
          el('tbody', {}, run.adversarial_checks.map((c) => el('tr', {},
            el('td', { text: c.case_id }), el('td', { text: c.description }),
            el('td', {}, c.safe ? badge('safe', 'green') : badge('UNSAFE', 'red')), el('td', { text: c.summary }))))))) : null,
      el('div', { class: 'section-label', text: 'Per case' }),
      el('div', { class: 'table-wrap' }, el('table', {},
        el('thead', {}, el('tr', {}, ['Case', 'Ticket', 'Result', 'Category', 'Priority', 'Draft', 'Citations', 'Actions', 'Answer checks', 'Notes']
          .map((h) => el('th', { text: h })))),
        el('tbody', {}, run.case_results.map((c) => el('tr', {},
          el('td', { text: c.case_id }),
          el('td', { text: c.ticket_id }),
          el('td', {}, c.passed ? badge('pass', 'green') : badge('fail', 'red')),
          el('td', { text: (c.predicted_category || '—') + (c.category_correct ? '' : ' (expected ' + c.expected_category + ')') }),
          el('td', { text: (c.predicted_priority || '—') + (c.priority_correct ? '' : ' (expected ' + c.expected_priority + ')') }),
          el('td', {}, c.draft_status ? badge(c.draft_status, statusColor(c.draft_status)) : '—'),
          el('td', { text: c.citations.join(', ') + (c.missing_citations.length ? ' · missing ' + c.missing_citations.join(', ') : '') }),
          el('td', { text: c.recommended_actions.join(', ') || 'none' }),
          el('td', { text: c.answer_checks.filter((a) => a.result === 'passed').length + '/' + c.answer_checks.length }),
          el('td', { text: c.error || c.notes }))))))));
}

function initEval() {
  const button = document.getElementById('run-eval');
  button.addEventListener('click', () => withBusy(button, 'Starting…', async () => {
    const run = await api('POST', '/eval-runs');
    renderEval(run);
    toast('Evaluation started in the background. This page updates every few seconds.');
    pollEval(run.eval_run_id);
  }));
}

// ---------- start ----------

initToken();
initTabs();
initEval();
document.getElementById('refresh-tickets').addEventListener('click', loadTickets);
loadTickets().then(() => {
  // Deep links for demos: /#ticket=tkt_9001 opens a ticket, /#view=evals opens the evaluations.
  const params = hashParams();
  if (params.get('view') === 'evals') showView('evals');
  else if (params.get('ticket') && state.token) openTicket(params.get('ticket'));
});
