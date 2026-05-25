const REST_BASE = 'http://localhost:8000/api';
const STREAM_BASE = REST_BASE;

const startBtn = document.getElementById('startBtn');
const playBtn = document.getElementById('playBtn');
const statusEl = document.getElementById('status');
const errorEl = document.getElementById('error');
const logEl = document.getElementById('log');
const cells = Array.from(document.querySelectorAll('.cell'));

let activeStream = null;
let manualGameId = null;
let manualNextPlayer = 'X';
let manualActive = false;
let manualMovePending = false;

function cell(r, c) {
    return cells.find(el => +el.dataset.r === r && +el.dataset.c === c);
}

function clearBoard() {
    cells.forEach(el => {
        el.textContent = '';
        el.classList.remove('X', 'O', 'last', 'winner', 'playable');
    });
    logEl.innerHTML = '';
    errorEl.hidden = true;
    errorEl.textContent = '';
    statusEl.className = 'status';
    statusEl.textContent = 'In progress...';
}

function stopActiveStream() {
    if (activeStream) {
        activeStream.close();
        activeStream = null;
    }
}

function setControlsLocked(locked) {
    startBtn.disabled = locked;
    playBtn.disabled = locked;
}

function renderBoard(board, lastMove) {
    cells.forEach(el => el.classList.remove('last'));
    for (let r = 0; r < 3; r++) {
        for (let c = 0; c < 3; c++) {
            const val = board[r][c];
            const el = cell(r, c);
            const existing = el.querySelector('span');
            if (val) {
                el.classList.remove('X', 'O');
                el.classList.add(val);
                // Only re-render if value changed — avoids re-triggering animation on every event
                if (!existing || existing.textContent !== val) {
                    el.innerHTML = `<span>${val}</span>`;
                }
            } else {
                el.classList.remove('X', 'O');
                el.innerHTML = '';
            }
        }
    }
    if (lastMove) {
        cell(lastMove.row, lastMove.col).classList.add('last');
    }
}

function setFinalStatus(state) {
    if (state.status === 'X_WON') {
        statusEl.className = 'status win';
        statusEl.textContent = 'X wins!';
    } else if (state.status === 'O_WON') {
        statusEl.className = 'status win';
        statusEl.textContent = 'O wins!';
    } else if (state.status === 'DRAW') {
        statusEl.className = 'status draw';
        statusEl.textContent = 'Draw';
    }
}

function getWinningCells(board) {
    const lines = [
        [[0, 0], [0, 1], [0, 2]],
        [[1, 0], [1, 1], [1, 2]],
        [[2, 0], [2, 1], [2, 2]],
        [[0, 0], [1, 0], [2, 0]],
        [[0, 1], [1, 1], [2, 1]],
        [[0, 2], [1, 2], [2, 2]],
        [[0, 0], [1, 1], [2, 2]],
        [[0, 2], [1, 1], [2, 0]],
    ];

    return lines.find(line => {
        const [[r1, c1], [r2, c2], [r3, c3]] = line;
        const value = board[r1][c1];
        return value && value === board[r2][c2] && value === board[r3][c3];
    }) || [];
}

function markWinningCells(board) {
    getWinningCells(board).forEach(([r, c]) => {
        cell(r, c).classList.add('winner');
    });
}

function showError(msg) {
    errorEl.hidden = false;
    errorEl.textContent = msg;
    statusEl.className = 'status err';
    statusEl.textContent = 'Error';
}

async function readErrorMessage(response) {
    try {
        const payload = await response.json();
        return payload.message || payload.error || response.statusText;
    } catch (e) {
        return response.statusText || `HTTP ${response.status}`;
    }
}

function appendMove(player, row, col) {
    const idx = logEl.children.length + 1;
    const li = document.createElement('li');
    li.textContent = `${idx}. ${player} → (${row},${col})`;
    logEl.appendChild(li);
    logEl.scrollTop = logEl.scrollHeight;
}

function updateManualStatus(state) {
    if (state.status === 'IN_PROGRESS') {
        statusEl.className = 'status';
        statusEl.textContent = `${state.nextPlayer} to move`;
        return;
    }

    setFinalStatus(state);
    markWinningCells(state.board);
    manualActive = false;
    cells.forEach(el => el.classList.remove('playable'));
    setControlsLocked(false);
}

async function startManualGame() {
    stopActiveStream();
    manualActive = false;
    manualMovePending = false;
    setControlsLocked(true);
    clearBoard();

    try {
        const createResp = await fetch(`${REST_BASE}/sessions`, { method: 'POST' });
        if (!createResp.ok) throw new Error(await readErrorMessage(createResp));
        const session = await createResp.json();

        manualGameId = session.gameId;
        manualNextPlayer = 'X';
        manualActive = true;
        renderBoard(session.board, null);
        statusEl.className = 'status';
        statusEl.textContent = `${manualNextPlayer} to move`;
        cells.forEach(el => el.classList.add('playable'));
    } catch (e) {
        showError(`Could not start manual game: ${e.message}`);
        setControlsLocked(false);
    }
}

async function applyManualMove(row, col) {
    if (!manualActive || manualMovePending) return;
    const target = cell(row, col);
    if (target.textContent.trim()) return;

    manualMovePending = true;
    try {
        const player = manualNextPlayer;
        const moveResp = await fetch(`${REST_BASE}/engine/games/${manualGameId}/move`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ player, row, col }),
        });
        if (!moveResp.ok) throw new Error(await readErrorMessage(moveResp));

        const state = await moveResp.json();
        renderBoard(state.board, { row, col });
        appendMove(player, row, col);
        manualNextPlayer = state.nextPlayer;
        updateManualStatus(state);
    } catch (e) {
        showError(`Could not apply move: ${e.message}`);
    } finally {
        manualMovePending = false;
    }
}

async function startSimulation() {
    stopActiveStream();
    manualActive = false;
    setControlsLocked(true);
    clearBoard();

    let session;
    try {
        const createResp = await fetch(`${REST_BASE}/sessions`, { method: 'POST' });
        if (!createResp.ok) throw new Error(`Create failed: ${createResp.status}`);
        session = await createResp.json();
    } catch (e) {
        showError(`Could not create session: ${e.message}`);
        setControlsLocked(false);
        return;
    }

    const sessionId = session.sessionId;
    const es = new EventSource(`${STREAM_BASE}/sessions/${sessionId}/stream`);
    activeStream = es;
    let simulationStarted = false;

    function requestSimulation() {
        if (simulationStarted) return;
        simulationStarted = true;
        fetch(`${REST_BASE}/sessions/${sessionId}/simulate-async`, { method: 'POST' })
            .catch(e => showError(`Could not start simulation: ${e.message}`));
    }

    es.addEventListener('ready', requestSimulation);

    es.addEventListener('state', (e) => {
        const state = JSON.parse(e.data);
        renderBoard(state.board, null);
    });

    es.addEventListener('move', (e) => {
        const payload = JSON.parse(e.data);
        renderBoard(payload.state.board, payload.move);
        const idx = logEl.children.length + 1;
        const li = document.createElement('li');
        li.textContent = `${idx}. ${payload.move.player} → (${payload.move.row},${payload.move.col})`;
        logEl.appendChild(li);
        logEl.scrollTop = logEl.scrollHeight;
    });

    es.addEventListener('finished', (e) => {
        const state = JSON.parse(e.data);
        setFinalStatus(state);
        markWinningCells(state.board);
        es.close();
        activeStream = null;
        setControlsLocked(false);
    });

    es.addEventListener('failed', (e) => {
        const payload = JSON.parse(e.data);
        showError(payload.message || 'Simulation failed');
        es.close();
        activeStream = null;
        setControlsLocked(false);
    });

    es.onerror = () => {
        // EventSource fires onerror also on normal close; only treat as error if not finished
        if (statusEl.textContent === 'In progress...') {
            showError('Lost connection to session service');
        }
        es.close();
        activeStream = null;
        setControlsLocked(false);
    };
}

startBtn.addEventListener('click', startSimulation);
playBtn.addEventListener('click', startManualGame);
cells.forEach(el => {
    el.addEventListener('click', () => {
        applyManualMove(+el.dataset.r, +el.dataset.c);
    });
});
