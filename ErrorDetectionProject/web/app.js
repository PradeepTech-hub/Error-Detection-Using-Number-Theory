const $ = (id) => document.getElementById(id);

const el = {
  status: $("status"),
  themeToggle: $("themeToggle"),
  sentData: $("sentData"),
  prime: $("prime"),
  receivedData: $("receivedData"),
  senderChecksum: $("senderChecksum"),
  senderModulo: $("senderModulo"),
  receiverChecksum: $("receiverChecksum"),
  receiverModulo: $("receiverModulo"),
  txFrameSender: $("txFrameSender"),
  txFrameReceiver: $("txFrameReceiver"),
  result: $("result"),
  resultTitle: null,
  resultBody: null,
  visual: $("visual"),
  errorDelta: $("errorDelta"),
  errorMode: $("errorMode"),
  errorPos: $("errorPos"),
  speed: $("speed"),
  btnSimulate: $("btnSimulate"),
  btnPrev: $("btnPrev"),
  btnNext: $("btnNext"),
  btnPause: $("btnPause"),
  btnStop: $("btnStop"),
  btnClear: $("btnClear"),
  btnCompute: $("btnCompute"),
  btnCopy: $("btnCopy"),
  btnError: $("btnError"),
  btnVerify: $("btnVerify"),
  btnReset: $("btnReset"),
  historyBody: $("historyBody"),
  btnExportCsv: $("btnExportCsv"),
  btnClearHistory: $("btnClearHistory"),

  // Binary ↔ Decimal converter
  binInput: $("binInput"),
  decInput: $("decInput"),
  btnBinToDec: $("btnBinToDec"),
  btnDecToBin: $("btnDecToBin"),
  btnConvClear: $("btnConvClear"),
  convResult: $("convResult"),
};

// Result sub-elements
(() => {
  const title = el.result.querySelector(".result-title");
  const body = el.result.querySelector(".result-body");
  el.resultTitle = title;
  el.resultBody = body;
})();

function setPill(kind, text) {
  el.status.className = `pill pill-${kind}`;
  el.status.textContent = text;
}

function setResult(kind, title, body) {
  el.result.className = `result result-${kind}`;
  el.resultTitle.textContent = title;
  el.resultBody.textContent = body;
}

function parseIntOrNull(v) {
  const t = (v ?? "").trim();
  if (!t) return null;
  if (t === "-") return null;
  const n = Number(t);
  if (!Number.isInteger(n)) return null;
  return n;
}

function formatFrame(data, checksum) {
  return `data=${data} | checksum=${checksum}`;
}

function formatModuloProcess(value, keyModulus, remainder) {
  const m = Math.abs(Number(keyModulus));
  if (!Number.isFinite(m) || m === 0) return "-";
  const v = Number(value);
  const r = Number(remainder);
  if (!Number.isInteger(v) || !Number.isInteger(r)) return "-";
  // Since remainder comes from Java's floorMod, q is an exact integer:
  // value = q*m + remainder
  const q = (v - r) / m;
  const qText = Number.isInteger(q) ? String(q) : String(Math.trunc(q));
  const mText = String(m);
  return `${v} mod ${mText} = ${r}  (because ${v} = ${qText}×${mText} + ${r})`;
}

function setTxFrame(data, checksum) {
  const text = (data == null || checksum == null) ? "-" : formatFrame(data, checksum);
  if (el.txFrameSender) el.txFrameSender.textContent = text;
  if (el.txFrameReceiver) el.txFrameReceiver.textContent = text;
}

function nowIso() {
  return new Date().toISOString();
}

function formatTime(iso) {
  try {
    const d = new Date(iso);
    return d.toLocaleString();
  } catch {
    return iso;
  }
}

// Theme
function loadTheme() {
  const t = localStorage.getItem("ed.theme") ?? "dark";
  document.body.classList.toggle("theme-light", t === "light");
}

function toggleTheme() {
  const isLight = document.body.classList.toggle("theme-light");
  localStorage.setItem("ed.theme", isLight ? "light" : "dark");
}

function wait(ms) {
  return new Promise((r) => setTimeout(r, ms));
}

async function waitControlled(ms, token) {
  if (!ms || ms <= 0) return;
  let remaining = ms;
  while (remaining > 0) {
    if (token?.cancelled) throw new Error("Stopped");
    while (token?.paused) {
      if (token?.cancelled) throw new Error("Stopped");
      await wait(120);
    }
    const chunk = Math.min(120, remaining);
    await wait(chunk);
    remaining -= chunk;
  }
}

function clearTimeline() {
  if (!el.visual) return;
  el.visual.innerHTML = "";
}

function appendNode(node) {
  if (!el.visual) return;
  el.visual.appendChild(node);
  el.visual.scrollTop = el.visual.scrollHeight;
}

function showTitle(text, subtext) {
  const t = document.createElement("div");
  t.className = "step-title fade";
  t.textContent = text;
  appendNode(t);

  if (subtext) {
    const s = document.createElement("div");
    s.className = "step-sub fade";
    s.textContent = subtext;
    appendNode(s);
  }
}

function showDigits(value, cls) {
  const str = String(value);
  const row = document.createElement("div");
  row.className = "bits fade";
  for (const ch of str) {
    const d = document.createElement("div");
    d.className = `bit ${cls}`;
    d.textContent = ch;
    row.appendChild(d);
  }
  appendNode(row);
}

function showKvRow(items) {
  const row = document.createElement("div");
  row.className = "kvrow fade";
  for (const text of items) {
    const pill = document.createElement("div");
    pill.className = "kvpill";
    pill.textContent = text;
    row.appendChild(pill);
  }
  appendNode(row);
}

function onlyIntegerInput(input) {
  input.addEventListener("input", () => {
    const raw = input.value;
    // allow empty or '-' while typing
    if (raw === "" || raw === "-") return;
    // keep digits and optional leading '-'
    const cleaned = raw.replace(/(?!^)-/g, "").replace(/[^0-9-]/g, "");
    if (cleaned !== raw) input.value = cleaned;
  });
}

function onlyBinaryInput(input) {
  input.addEventListener("input", () => {
    const raw = input.value;
    if (raw === "" || raw === "-") return;
    // keep only 0/1 and optional leading '-'
    const cleaned = raw.replace(/(?!^)-/g, "").replace(/[^01-]/g, "");
    if (cleaned !== raw) input.value = cleaned;
  });
}

onlyIntegerInput(el.sentData);
onlyIntegerInput(el.prime);
onlyIntegerInput(el.receivedData);
if (el.errorDelta) onlyIntegerInput(el.errorDelta);
if (el.errorPos) onlyIntegerInput(el.errorPos);

// Converter inputs
if (el.binInput) onlyBinaryInput(el.binInput);
if (el.decInput) onlyIntegerInput(el.decInput);

loadTheme();
if (el.themeToggle) {
  el.themeToggle.addEventListener("click", toggleTheme);
}

// History
const HISTORY_KEY = "ed.history";

function loadHistory() {
  try {
    const raw = localStorage.getItem(HISTORY_KEY);
    const arr = raw ? JSON.parse(raw) : [];
    return Array.isArray(arr) ? arr : [];
  } catch {
    return [];
  }
}

function saveHistory(entries) {
  localStorage.setItem(HISTORY_KEY, JSON.stringify(entries));
}

function addHistoryEntry(entry) {
  const entries = loadHistory();
  entries.unshift(entry);
  // Cap history to avoid unbounded growth
  const capped = entries.slice(0, 60);
  saveHistory(capped);
  renderHistory();
}

function escapeCsv(v) {
  const s = String(v ?? "");
  if (/[",\n]/.test(s)) return `"${s.replaceAll('"', '""')}"`;
  return s;
}

// CSV export is implemented in exportHistoryCsvFixed().

function renderHistory() {
  if (!el.historyBody) return;
  const entries = loadHistory();
  el.historyBody.innerHTML = "";

  for (const e of entries) {
    const tr = document.createElement("tr");

    const tdTime = document.createElement("td");
    tdTime.textContent = formatTime(e.time);

    const tdSent = document.createElement("td");
    tdSent.textContent = String(e.sentData);

    const tdPrime = document.createElement("td");
    tdPrime.textContent = String(e.prime);

    const tdRecv = document.createElement("td");
    tdRecv.textContent = String(e.receivedData);

    const tdScs = document.createElement("td");
    tdScs.textContent = String(e.senderChecksum);

    const tdRcs = document.createElement("td");
    tdRcs.textContent = String(e.receiverChecksum);

    const tdOk = document.createElement("td");
    const badge = document.createElement("span");
    badge.className = `badge ${e.ok ? "badge-ok" : "badge-bad"}`;
    badge.textContent = e.ok ? "OK" : "ERROR";
    tdOk.appendChild(badge);

    const tdSrc = document.createElement("td");
    tdSrc.textContent = e.source ?? "";

    tr.appendChild(tdTime);
    tr.appendChild(tdSent);
    tr.appendChild(tdPrime);
    tr.appendChild(tdRecv);
    tr.appendChild(tdScs);
    tr.appendChild(tdRcs);
    tr.appendChild(tdOk);
    tr.appendChild(tdSrc);

    el.historyBody.appendChild(tr);
  }
}

function downloadCsv(filename, csvText) {
  const blob = new Blob([csvText], { type: "text/csv;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}

function exportHistoryCsvFixed() {
  const entries = loadHistory();
  const header = ["time", "sentData", "keyModulus", "receivedData", "senderChecksum", "receiverChecksum", "ok", "source"].join(",");
  const rows = entries.map((e) => [
    escapeCsv(e.time),
    escapeCsv(e.sentData),
    escapeCsv(e.prime),
    escapeCsv(e.receivedData),
    escapeCsv(e.senderChecksum),
    escapeCsv(e.receiverChecksum),
    escapeCsv(e.ok),
    escapeCsv(e.source),
  ].join(","));

  const csv = [header, ...rows].join("\n");
  downloadCsv("error-detection-history.csv", csv);
}

if (el.btnExportCsv) {
  el.btnExportCsv.addEventListener("click", exportHistoryCsvFixed);
}

if (el.btnClearHistory) {
  el.btnClearHistory.addEventListener("click", () => {
    saveHistory([]);
    renderHistory();
  });
}

renderHistory();

// Binary ↔ Decimal Converter
function setConvResult(text) {
  if (!el.convResult) return;
  el.convResult.textContent = text;
}

function normalizeBinaryString(s) {
  const t = (s ?? "").trim();
  if (!t) return null;
  if (t === "-") return null;
  if (!/^-?[01]+$/.test(t)) return null;
  // normalize leading zeros for display (keep single '0')
  const neg = t.startsWith("-");
  const bits = (neg ? t.slice(1) : t).replace(/^0+(?=.)/, "");
  return (neg ? "-" : "") + bits;
}

function binToBigInt(binStr) {
  const norm = normalizeBinaryString(binStr);
  if (norm == null) return null;
  const neg = norm.startsWith("-");
  const bits = neg ? norm.slice(1) : norm;
  // BigInt supports 0b prefix for binary
  const n = BigInt("0b" + bits);
  return neg ? -n : n;
}

function decToBigInt(decStr) {
  const t = (decStr ?? "").trim();
  if (!t) return null;
  if (t === "-") return null;
  if (!/^-?\d+$/.test(t)) return null;
  try {
    return BigInt(t);
  } catch {
    return null;
  }
}

function bigIntToBinaryString(n) {
  const neg = n < 0n;
  const abs = neg ? -n : n;
  const bits = abs.toString(2);
  return (neg ? "-" : "") + bits;
}

function explainBinToDecShort(binStr) {
  const norm = normalizeBinaryString(binStr);
  if (norm == null) return null;

  const neg = norm.startsWith("-");
  const bits = neg ? norm.slice(1) : norm;
  // Keep explanation readable: if too long, skip breakdown.
  if (bits.length > 32) return `${norm}₂ → (too many bits to show steps) → ${String(binToBigInt(norm))}₁₀`;

  let sum = 0n;
  const terms = [];
  const len = bits.length;
  for (let i = 0; i < len; i++) {
    const ch = bits[i];
    const power = BigInt(len - 1 - i);
    if (ch === "1") {
      const term = 1n << power;
      sum += term;
      terms.push(String(term));
    }
  }
  const final = neg ? -sum : sum;
  const termText = terms.length ? terms.join(" + ") : "0";
  return `${norm}₂ = ${termText} = ${String(final)}₁₀`;
}

if (el.btnBinToDec) {
  el.btnBinToDec.addEventListener("click", () => {
    const n = binToBigInt(el.binInput?.value ?? "");
    if (n == null) {
      setConvResult("Enter a valid binary value (only 0/1, optional leading '-')");
      return;
    }
    const pretty = explainBinToDecShort(el.binInput?.value ?? "") ?? `${normalizeBinaryString(el.binInput?.value ?? "")}₂ → ${String(n)}₁₀`;
    setConvResult(pretty);
    if (el.decInput) el.decInput.value = String(n);
  });
}

if (el.btnDecToBin) {
  el.btnDecToBin.addEventListener("click", () => {
    const n = decToBigInt(el.decInput?.value ?? "");
    if (n == null) {
      setConvResult("Enter a valid decimal integer (optional leading '-')");
      return;
    }
    const bin = bigIntToBinaryString(n);
    setConvResult(`${String(n)}₁₀ → ${bin}₂`);
    if (el.binInput) el.binInput.value = bin;
  });
}

if (el.btnConvClear) {
  el.btnConvClear.addEventListener("click", () => {
    if (el.binInput) el.binInput.value = "";
    if (el.decInput) el.decInput.value = "";
    setConvResult("-");
  });
}

let lastSenderChecksum = null;

async function verify(sentData, prime, receivedData) {
  const url = new URL("/api/verify", window.location.origin);
  url.searchParams.set("sentData", String(sentData));
  url.searchParams.set("prime", String(prime));
  url.searchParams.set("receivedData", String(receivedData));

  const res = await fetch(url.toString(), { method: "GET" });
  const data = await res.json().catch(() => null);
  if (!res.ok) {
    const msg = data?.message ?? `Request failed (${res.status})`;
    throw new Error(msg);
  }
  return data;
}

function getPlayMode() {
  const checked = document.querySelector('input[name="playMode"]:checked');
  return checked?.value ?? "auto";
}

function computeReceivedData(sentData) {
  const delta = parseIntOrNull(el.errorDelta?.value ?? "") ?? 0;
  if (delta === 0) return { receivedData: sentData, description: "No error (delta = 0)" };
  return { receivedData: sentData + delta, description: `Add delta ${delta}` };
}

function getExecutionMode() {
  const checked = document.querySelector('input[name="execMode"]:checked');
  return checked?.value ?? "stop";
}

el.btnCompute.addEventListener("click", async () => {
  const sentData = parseIntOrNull(el.sentData.value);
  const prime = parseIntOrNull(el.prime.value);

  if (sentData == null || prime == null) {
    setPill("bad", "Enter data + key/modulus");
    setResult("bad", "Input error", "Please fill both Data and Key/Modulus.");
    return;
  }

  // We'll compute sender checksum by calling API with receivedData=sentData.
  try {
    const r = await verify(sentData, prime, sentData);
    lastSenderChecksum = r.senderChecksum;
    el.senderChecksum.textContent = String(r.senderChecksum);
    el.receiverChecksum.textContent = "-";
    if (el.senderModulo) el.senderModulo.textContent = formatModuloProcess(sentData, prime, r.senderChecksum);
    if (el.receiverModulo) el.receiverModulo.textContent = "-";
    setTxFrame(sentData, r.senderChecksum);
    setPill("info", "Step 2: Set received data (channel)");
    setResult("neutral", "Sender ready", "Now copy sent -> received, optionally introduce an error.");
  } catch (e) {
    setPill("bad", e.message);
    setResult("bad", "Cannot compute", e.message);
  }
});

el.btnCopy.addEventListener("click", () => {
  if (lastSenderChecksum == null) {
    setPill("bad", "Compute sender checksum first");
    setResult("bad", "Step order", "Compute sender checksum (Step 1) before copying.");
    return;
  }
  el.receivedData.value = (el.sentData.value ?? "").trim();
  setPill("info", "Step 3: Verify at receiver");
  setResult("neutral", "Channel set", "Click Verify to detect errors.");
});

el.btnError.addEventListener("click", () => {
  if (lastSenderChecksum == null) {
    setPill("bad", "Compute sender checksum first");
    setResult("bad", "Step order", "Compute sender checksum (Step 1) before introducing errors.");
    return;
  }
  const r = parseIntOrNull(el.receivedData.value);
  if (r == null) {
    setPill("bad", "Enter/copy received data first");
    setResult("bad", "Missing received data", "Copy sent -> received first, then introduce an error.");
    return;
  }
  const delta = parseIntOrNull(el.errorDelta?.value ?? "") ?? 1;
  el.receivedData.value = String(r + delta);
  setPill("info", "Step 3: Verify at receiver");
  setResult("neutral", "Error injected", "Now click Verify to see detection.");
});

el.btnVerify.addEventListener("click", async () => {
  const sentData = parseIntOrNull(el.sentData.value);
  const prime = parseIntOrNull(el.prime.value);
  const receivedData = parseIntOrNull(el.receivedData.value);

  if (sentData == null || prime == null || receivedData == null) {
    setPill("bad", "Fill all inputs (steps 1-2)");
    setResult("bad", "Input error", "Enter Data, Key/Modulus, and Received data.");
    return;
  }

  try {
    const r = await verify(sentData, prime, receivedData);
    el.senderChecksum.textContent = String(r.senderChecksum);
    el.receiverChecksum.textContent = String(r.receiverChecksum);
    if (el.senderModulo) el.senderModulo.textContent = formatModuloProcess(sentData, prime, r.senderChecksum);
    if (el.receiverModulo) el.receiverModulo.textContent = formatModuloProcess(receivedData, prime, r.receiverChecksum);
    setTxFrame(sentData, r.senderChecksum);

    if (r.ok) {
      setPill("ok", "No Error Detected");
      setResult("ok", "No Error Detected", "Checksums match. Data is not corrupted.");
    } else {
      setPill("bad", "Error Detected");
      setResult("bad", "Error Detected", "Checksums differ. Data is corrupted.");
    }

    addHistoryEntry({
      time: nowIso(),
      sentData,
      prime,
      receivedData,
      senderChecksum: r.senderChecksum,
      receiverChecksum: r.receiverChecksum,
      ok: !!r.ok,
      source: "manual-verify",
    });
  } catch (e) {
    setPill("bad", e.message);
    setResult("bad", "Verification failed", e.message);
  }
});

el.btnReset.addEventListener("click", () => {
  el.sentData.value = "";
  el.prime.value = "";
  el.receivedData.value = "";
  el.senderChecksum.textContent = "-";
  el.receiverChecksum.textContent = "-";
  if (el.senderModulo) el.senderModulo.textContent = "-";
  if (el.receiverModulo) el.receiverModulo.textContent = "-";
  setTxFrame(null, null);
  lastSenderChecksum = null;
  setPill("neutral", "Step 1: Enter data + key/modulus");
  setResult("neutral", "Ready", "Compute sender checksum, set received data, then verify.");
});

if (el.btnClear) {
  el.btnClear.addEventListener("click", () => {
    clearTimeline();
    setPill("neutral", "Step 1: Enter data + key/modulus");
  });
}

if (el.btnSimulate) {
  const sim = {
    token: null,
    ctx: null,
    steps: [],
    stepIndex: 0,
    recorded: false,
  };

  function updateSimButtons() {
    const playMode = getPlayMode();
    const running = !!sim.token && !sim.token.cancelled;
    const paused = !!sim.token?.paused;

    if (el.btnPrev) el.btnPrev.disabled = playMode !== "step" || sim.stepIndex <= 0;
    if (el.btnNext) el.btnNext.disabled = playMode !== "step" || sim.stepIndex >= sim.steps.length - 1;
    if (el.btnPause) {
      el.btnPause.disabled = playMode !== "auto" || !running;
      el.btnPause.textContent = paused ? "Resume" : "Pause";
    }
    if (el.btnStop) el.btnStop.disabled = !running;
  }

  updateSimButtons();

  async function renderTo(index, { delayed, token } = { delayed: false, token: null }) {
    clearTimeline();
    for (let i = 0; i <= index; i++) {
      if (token?.cancelled) throw new Error("Stopped");
      await sim.steps[i]();
      if (delayed) await waitControlled(sim.ctx.speedMs, token);
    }
  }

  function maybeRecordHistory(result, source) {
    if (sim.recorded) return;
    sim.recorded = true;
    addHistoryEntry({
      time: nowIso(),
      sentData: sim.ctx.sentData,
      prime: sim.ctx.prime,
      receivedData: sim.ctx.receivedData,
      senderChecksum: result.senderChecksum,
      receiverChecksum: result.receiverChecksum,
      ok: !!result.ok,
      source,
    });
  }

  async function buildSimulation() {
    const sentData = parseIntOrNull(el.sentData.value);
    const prime = parseIntOrNull(el.prime.value);
    const speedMs = parseInt(el.speed?.value ?? "650", 10);
    const execMode = getExecutionMode();
    const playMode = getPlayMode();

    if (sentData == null || prime == null) {
      setPill("bad", "Enter data + key/modulus");
      setResult("bad", "Input error", "Please fill both Data and Key/Modulus.");
      showTitle("Input Error", "Enter both Data and Key/Modulus.");
      return false;
    }

    const error = computeReceivedData(sentData);
    const receivedData = error.receivedData;
    el.receivedData.value = String(receivedData);

    const senderRes = await verify(sentData, prime, sentData);
    const recvRes = await verify(sentData, prime, receivedData);

    // Keep the top-level UI in sync as well.
    setTxFrame(sentData, senderRes.senderChecksum);

    sim.ctx = { sentData, prime, receivedData, speedMs, execMode, playMode, errorDesc: error.description, senderRes, recvRes };
    sim.steps = [];
    sim.stepIndex = 0;
    sim.recorded = false;

    sim.steps.push(async () => {
      showTitle("Step 1: Sender Data", "User input at sender side");
      showDigits(sentData, "data");
    });

    sim.steps.push(async () => {
      showTitle("Step 2: Key/Modulus", "Key for checksum (any non-zero integer)");
      showDigits(prime, "gen");
    });

    sim.steps.push(async () => {
      showTitle("Step 3: Sender Checksum", "senderChecksum = sentData mod key/modulus");
      lastSenderChecksum = senderRes.senderChecksum;
      el.senderChecksum.textContent = String(senderRes.senderChecksum);
      if (el.senderModulo) el.senderModulo.textContent = formatModuloProcess(sentData, prime, senderRes.senderChecksum);
      showKvRow([
        `sentData = ${sentData}`,
        `key/modulus = ${prime}`,
        `checksum = ${senderRes.senderChecksum}`,
        formatModuloProcess(sentData, prime, senderRes.senderChecksum),
      ]);
      showDigits(senderRes.senderChecksum, "rem");
    });

    sim.steps.push(async () => {
      showTitle("Step 4: Transmitted Frame", "Frame contains the data and checksum");
      showKvRow([`frame.data = ${sentData}`, `frame.checksum = ${senderRes.senderChecksum}`]);
    });

    sim.steps.push(async () => {
      const isError = receivedData !== sentData;
      showTitle("Step 5: Channel", isError ? `Error injected: ${sim.ctx.errorDesc}` : "No error introduced");
      showKvRow([`receivedData = ${receivedData}`]);
      showDigits(receivedData, isError ? "xor" : "zero");
    });

    sim.steps.push(async () => {
      showTitle("Step 6: Receiver Checksum", "receiverChecksum = receivedData mod key/modulus");
      el.receiverChecksum.textContent = String(recvRes.receiverChecksum);
      if (el.receiverModulo) el.receiverModulo.textContent = formatModuloProcess(receivedData, prime, recvRes.receiverChecksum);
      showKvRow([
        `receivedData = ${receivedData}`,
        `key/modulus = ${prime}`,
        `checksum = ${recvRes.receiverChecksum}`,
        formatModuloProcess(receivedData, prime, recvRes.receiverChecksum),
      ]);
      showDigits(recvRes.receiverChecksum, "rem");
    });

    sim.steps.push(async () => {
      showTitle("Step 7: Compare Checksums", "If checksums match, data is accepted");
      showKvRow([`senderChecksum = ${recvRes.senderChecksum}`, `receiverChecksum = ${recvRes.receiverChecksum}`]);
    });

    sim.steps.push(async () => {
      if (!recvRes.ok && execMode === "stop") {
        setPill("bad", "Error Detected (execution stopped)");
        setResult("bad", "Error Detected", "Checksums differ. Flow stopped because 'Stop on error' is selected.");
        showTitle("Execution Stopped", "Error detected - flow terminated by setting");
        showKvRow([`transmitted.frame.data = ${sentData}`, `transmitted.frame.checksum = ${senderRes.senderChecksum}`]);
      } else if (recvRes.ok) {
        setPill("ok", "No Error Detected");
        setResult("ok", "No Error Detected", "Checksums match. Data is not corrupted.");
        showTitle("Final Result", "No Error Detected (frame accepted)");
        showKvRow([`transmitted.frame.data = ${sentData}`, `transmitted.frame.checksum = ${senderRes.senderChecksum}`]);
      } else {
        setPill("bad", "Error Detected");
        setResult("bad", "Error Detected", "Checksums differ. Data is corrupted.");
        showTitle("Final Result", "Error Detected - Frame rejected");
        showKvRow([`transmitted.frame.data = ${sentData}`, `transmitted.frame.checksum = ${senderRes.senderChecksum}`]);
      }
    });

    return true;
  }

  el.btnSimulate.addEventListener("click", async () => {
    clearTimeline();
    setPill("info", "Preparing simulation...");
    setResult("neutral", "Simulation", "Building steps...");
    try {
      const ok = await buildSimulation();
      if (!ok) return;

      const playMode = getPlayMode();
      if (playMode === "step") {
        sim.stepIndex = 0;
        await renderTo(sim.stepIndex, { delayed: false });
        setPill("info", "Step mode: use Next/Back");
        updateSimButtons();
        return;
      }

      sim.token = { cancelled: false, paused: false };
      sim.stepIndex = sim.steps.length - 1;
      updateSimButtons();

      setPill("info", "Running step-by-step workflow...");
      await renderTo(sim.steps.length - 1, { delayed: true, token: sim.token });

      maybeRecordHistory(sim.ctx.recvRes, "simulation-auto");
    } catch (e) {
      if (String(e?.message).includes("Stopped")) {
        setPill("bad", "Simulation stopped");
        showTitle("Stopped", "Simulation cancelled by user");
        if (sim?.ctx?.recvRes) maybeRecordHistory(sim.ctx.recvRes, "simulation-stopped");
      } else {
        setPill("bad", e.message);
        setResult("bad", "Simulation failed", e.message);
        showTitle("Simulation Error", e.message);
      }
    } finally {
      if (sim.token) {
        sim.token.cancelled = true;
        sim.token.paused = false;
      }
      sim.token = null;
      updateSimButtons();
    }
  });

  if (el.btnPrev) {
    el.btnPrev.addEventListener("click", async () => {
      try {
        if (getPlayMode() !== "step") return;
        sim.stepIndex = Math.max(0, sim.stepIndex - 1);
        await renderTo(sim.stepIndex, { delayed: false });
      } finally {
        updateSimButtons();
      }
    });
  }

  if (el.btnNext) {
    el.btnNext.addEventListener("click", async () => {
      try {
        if (getPlayMode() !== "step") return;
        sim.stepIndex = Math.min(sim.steps.length - 1, sim.stepIndex + 1);
        await renderTo(sim.stepIndex, { delayed: false });
        if (sim.stepIndex === sim.steps.length - 1 && sim?.ctx?.recvRes) {
          maybeRecordHistory(sim.ctx.recvRes, "simulation-step");
        }
      } finally {
        updateSimButtons();
      }
    });
  }

  if (el.btnPause) {
    el.btnPause.addEventListener("click", () => {
      if (!sim.token) return;
      sim.token.paused = !sim.token.paused;
      updateSimButtons();
    });
  }

  if (el.btnStop) {
    el.btnStop.addEventListener("click", () => {
      if (!sim.token) return;
      sim.token.cancelled = true;
    });
  }

  // Keyboard shortcuts
  document.addEventListener("keydown", (e) => {
    if (e.key === "Escape") {
      if (sim.token) sim.token.cancelled = true;
      return;
    }
    if (e.key === " ") {
      // Space
      if (sim.token && getPlayMode() === "auto") {
        e.preventDefault();
        sim.token.paused = !sim.token.paused;
        updateSimButtons();
      }
      return;
    }
    if (e.key === "ArrowLeft") {
      if (getPlayMode() === "step" && el.btnPrev && !el.btnPrev.disabled) {
        el.btnPrev.click();
      }
      return;
    }
    if (e.key === "ArrowRight") {
      if (getPlayMode() === "step" && el.btnNext && !el.btnNext.disabled) {
        el.btnNext.click();
      }
    }
  });
}
