// 예매 화면 공통 함수. 브라우저에는 Toss 클라이언트 키(백엔드 응답 payment.clientKey)만 들어온다. 시크릿 키는 두지 않는다.
const TOKEN_KEY = "fm.accessToken";

const STATUS_LABELS = {
  HOLD_REQUESTED: "좌석 확보 확인 중",
  HELD: "결제 대기",
  PAYMENT_CONFIRMING: "결제 확인 중",
  PAYMENT_APPROVED: "결제 승인, 발권 대기",
  DUFFEL_PAYING: "발권 진행 중",
  DUFFEL_PAYMENT_UNCONFIRMED: "결제 확인 중(발권 대기)",
  TICKETED: "발권 완료",
  FAILED: "예매 실패",
};

class ApiError extends Error {
  constructor(status, tag, message, body) {
    super(message);
    this.status = status;
    this.tag = tag;
    this.body = body;
  }
}

function getToken() {
  return localStorage.getItem(TOKEN_KEY);
}

function setToken(token) {
  localStorage.setItem(TOKEN_KEY, token.trim().replace(/^Bearer\s+/i, ""));
}

function clearToken() {
  localStorage.removeItem(TOKEN_KEY);
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function query(name) {
  return new URLSearchParams(location.search).get(name);
}

function $(id) {
  return document.getElementById(id);
}

function escapeHtml(value) {
  return String(value ?? "").replace(/[&<>"']/g, (c) =>
    ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]);
}

function statusLabel(status) {
  return STATUS_LABELS[status] ? `${STATUS_LABELS[status]} (${status})` : status;
}

function formatKrw(value) {
  return value == null ? "-" : `${Number(value).toLocaleString("ko-KR")}원`;
}

function showMessage(text, kind) {
  const el = $("msg");
  el.className = "msg" + (kind ? " " + kind : "");
  el.textContent = text || "";
}

function showError(e) {
  if (e instanceof ApiError && e.status === 401) {
    showMessage("로그인이 필요합니다. 첫 화면에서 토큰을 다시 입력해 주세요.", "error");
    return;
  }
  const tag = e instanceof ApiError && e.tag ? ` [${e.tag}]` : "";
  showMessage((e.message || String(e)) + tag, "error");
}

async function parseBody(res) {
  const text = await res.text();
  if (!text) return null;
  try {
    return JSON.parse(text);
  } catch {
    return { message: text };
  }
}

function headers(extra) {
  const h = { Accept: "application/json", ...extra };
  const token = getToken();
  if (token) h.Authorization = "Bearer " + token;
  return h;
}

function toError(res, data) {
  if (res.status === 401) clearToken();
  return new ApiError(res.status, data && data.tag, (data && data.message) || `요청 실패 (HTTP ${res.status})`, data);
}

/** GET. 2xx면 본문, 아니면 ApiError */
async function apiGet(path) {
  let res;
  try {
    res = await fetch(path, { headers: headers() });
  } catch {
    throw new ApiError(0, "NETWORK", "서버 응답을 받지 못했습니다. 잠시 후 다시 시도해 주세요.");
  }
  const data = await parseBody(res);
  if (!res.ok) throw toError(res, data);
  return data;
}

/**
 * 멱등키 POST. 응답을 못 받으면 같은 키로 최대 2회 재전송하고, 409 IDEMPOTENT_REQUEST_PROCESSING이면 2초 뒤 같은 키로 다시 보낸다.
 * 응답을 받으면 { status, data }를 돌려준다(2xx·오류 모두). 끝내 결과를 모르면 ApiError(tag: UNRESOLVED)를 던진다.
 */
async function idempotentPost(path, body, key) {
  let networkRetries = 0;
  let processingRetries = 0;
  for (;;) {
    let res;
    try {
      res = await fetch(path, {
        method: "POST",
        headers: headers({ "Content-Type": "application/json", "Idempotency-Key": key }),
        body: JSON.stringify(body),
      });
    } catch {
      if (networkRetries < 2) {
        networkRetries++;
        await sleep(1000);
        continue;
      }
      throw new ApiError(0, "UNRESOLVED", "서버 응답을 받지 못했습니다. 다시 누르면 같은 요청으로 이어서 확인합니다.");
    }
    const data = await parseBody(res);
    if (res.status === 409 && data && data.tag === "IDEMPOTENT_REQUEST_PROCESSING") {
      if (processingRetries < 30) {
        processingRetries++;
        await sleep(2000);
        continue;
      }
      throw new ApiError(409, "UNRESOLVED", "요청을 아직 처리하고 있습니다. 다시 누르면 같은 요청으로 이어서 확인합니다.");
    }
    if (res.status === 401) clearToken();
    return { status: res.status, data };
  }
}

/** 예매를 2초 간격으로 조회한다. done(booking)이 true이거나 timeoutMs가 지나면 마지막 예매를 돌려준다 */
async function pollBooking(bookingId, done, timeoutMs, onEach) {
  const until = Date.now() + timeoutMs;
  for (;;) {
    const booking = await apiGet(`/api/bookings/${encodeURIComponent(bookingId)}`);
    if (onEach) onEach(booking);
    if (done(booking) || Date.now() >= until) return booking;
    await sleep(2000);
  }
}

/** 상단 메뉴와 로그인 상태 */
function renderNav() {
  const nav = document.createElement("nav");
  nav.innerHTML = `<a href="/index.html">검색</a><a href="/bookings.html">예매 내역</a>`
    + `<span class="muted">${getToken() ? "토큰 입력됨" : "토큰 없음"}</span>`;
  document.body.prepend(nav);
}
