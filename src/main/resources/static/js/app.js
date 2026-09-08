const state = { result: null, files: [null, null] };

const elements = {
    form: document.querySelector('#compareForm'),
    error: document.querySelector('#formError'),
    empty: document.querySelector('#emptyState'),
    results: document.querySelector('#results'),
    prefix: document.querySelector('#prefix'),
    strategy: document.querySelector('#mergeStrategy'),
    ignoreWhitespace: document.querySelector('#ignoreWhitespace'),
    ignoreCase: document.querySelector('#ignoreCase')
};

function formatBytes(bytes) {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function bindFilePicker(index) {
    const number = index + 1;
    const input = document.querySelector(`#file${number}`);
    const card = document.querySelector(`#file${number}Card`);
    const name = document.querySelector(`#file${number}Name`);
    const meta = document.querySelector(`#file${number}Meta`);

    const setFile = (file) => {
        if (!file) return;
        state.files[index] = file;
        name.textContent = file.name;
        meta.textContent = `${formatBytes(file.size)} · ready to compare`;
        card.classList.add('active');
        elements.error.hidden = true;
    };

    input.addEventListener('change', () => setFile(input.files[0]));
    card.addEventListener('keydown', (event) => {
        if (event.key === 'Enter' || event.key === ' ') {
            event.preventDefault();
            input.click();
        }
    });
    ['dragenter', 'dragover'].forEach(type => card.addEventListener(type, (event) => {
        event.preventDefault();
        card.classList.add('dragging');
    }));
    ['dragleave', 'drop'].forEach(type => card.addEventListener(type, (event) => {
        event.preventDefault();
        card.classList.remove('dragging');
    }));
    card.addEventListener('drop', (event) => {
        const file = event.dataTransfer.files[0];
        if (!file) return;
        const transfer = new DataTransfer();
        transfer.items.add(file);
        input.files = transfer.files;
        setFile(file);
    });
}

function parseMessages(content, prefix) {
    const messages = new Map();
    const normalizedPrefix = prefix.trim();

    content.replace(/^\uFEFF/, '').split(/\r?\n/).forEach(rawLine => {
        const line = rawLine.trim();
        if (!line || line.startsWith('#') || line.startsWith('!')) return;

        let divider = line.indexOf('=');
        if (divider < 0) divider = line.indexOf('\t');
        if (divider < 0) divider = line.search(/\s/);
        if (divider <= 0) return;

        const key = line.slice(0, divider).trim();
        const value = line.slice(divider + 1).trim();
        if (!key || (normalizedPrefix && !key.startsWith(normalizedPrefix))) return;
        messages.set(key, value);
    });

    return messages;
}

function normalize(value) {
    let output = value;
    if (elements.ignoreWhitespace.checked) output = output.replace(/\s+/g, ' ').trim();
    if (elements.ignoreCase.checked) output = output.toLocaleLowerCase();
    return output;
}

function detectSeverity(first, second) {
    if (first.trim().toLocaleLowerCase() === second.trim().toLocaleLowerCase()) return 'LOW';

    const placeholders = value => value.match(/\{[^}]+}|%[a-zA-Z]|\$\{[^}]+}/g) || [];
    if (JSON.stringify(placeholders(first)) !== JSON.stringify(placeholders(second))) return 'HIGH';

    const important = new Set(['not', 'never', 'required', 'failed', 'error', 'warning', 'delete', 'removed', 'disabled']);
    const signalWords = value => [...new Set((value.toLocaleLowerCase().match(/[\p{L}\p{N}_]+/gu) || []).filter(word => important.has(word)))].sort();
    if (JSON.stringify(signalWords(first)) !== JSON.stringify(signalWords(second))) return 'HIGH';
    if (Math.abs(first.length - second.length) > 30) return 'MEDIUM';
    return 'LOW';
}

function compare(first, second) {
    const allKeys = [...new Set([...first.keys(), ...second.keys()])].sort((a, b) => a.localeCompare(b));
    const merged = [];
    const missingFirst = [];
    const missingSecond = [];
    const different = [];

    allKeys.forEach(key => {
        const hasFirst = first.has(key);
        const hasSecond = second.has(key);
        const firstValue = first.get(key);
        const secondValue = second.get(key);

        if (!hasFirst) missingFirst.push({ key, value: secondValue });
        if (!hasSecond) missingSecond.push({ key, value: firstValue });
        if (hasFirst && hasSecond && normalize(firstValue) !== normalize(secondValue)) {
            different.push({ key, first: firstValue, second: secondValue, severity: detectSeverity(firstValue, secondValue) });
        }

        const value = elements.strategy.value === 'first'
            ? (hasFirst ? firstValue : secondValue)
            : (hasSecond ? secondValue : firstValue);
        merged.push({ key, value });
    });

    return { firstCount: first.size, secondCount: second.size, merged, missingFirst, missingSecond, different };
}

function createCell(value, className) {
    const cell = document.createElement('td');
    if (className) cell.className = className;
    const pre = document.createElement('pre');
    pre.textContent = value;
    cell.appendChild(pre);
    return cell;
}

function renderPairRows(target, rows) {
    const body = document.querySelector(target);
    body.replaceChildren();
    if (!rows.length) {
        const row = document.createElement('tr');
        const cell = document.createElement('td');
        cell.colSpan = 2;
        cell.className = 'table-empty';
        cell.textContent = 'No entries in this section.';
        row.appendChild(cell);
        body.appendChild(row);
        return;
    }
    rows.forEach(item => {
        const row = document.createElement('tr');
        row.append(createCell(item.key), createCell(item.value));
        body.appendChild(row);
    });
}

function renderDifferences(rows) {
    const body = document.querySelector('#differentRows');
    body.replaceChildren();
    if (!rows.length) {
        const row = document.createElement('tr');
        const cell = document.createElement('td');
        cell.colSpan = 4;
        cell.className = 'table-empty';
        cell.textContent = 'The shared values match.';
        row.appendChild(cell);
        body.appendChild(row);
        return;
    }
    rows.forEach(item => {
        const row = document.createElement('tr');
        const severity = document.createElement('td');
        severity.className = `severity-${item.severity}`;
        severity.textContent = item.severity;
        row.append(createCell(item.key), createCell(item.first), createCell(item.second), severity);
        body.appendChild(row);
    });
}

function setCount(id, value) {
    document.querySelector(`#${id}`).textContent = value.toLocaleString();
}

function activateSection(targetId) {
    document.querySelectorAll('.section-card').forEach(section => section.classList.toggle('active', section.id === targetId));
    document.querySelectorAll('[data-target]').forEach(control => control.classList.toggle('active', control.dataset.target === targetId));
}

function renderResult(result) {
    state.result = result;
    setCount('firstCount', result.firstCount);
    setCount('secondCount', result.secondCount);
    setCount('missingFirstCount', result.missingFirst.length);
    setCount('missingSecondCount', result.missingSecond.length);
    setCount('differentCount', result.different.length);
    document.querySelector('#resultCaption').textContent = `${state.files[0].name} compared with ${state.files[1].name}`;
    renderPairRows('#mergedRows', result.merged);
    renderPairRows('#missingFirstRows', result.missingFirst);
    renderPairRows('#missingSecondRows', result.missingSecond);
    renderDifferences(result.different);
    elements.empty.hidden = true;
    elements.results.hidden = false;
    activateSection('mergedSection');
    elements.results.scrollIntoView({ behavior: 'smooth', block: 'start' });
}

function csvEscape(value) {
    const text = String(value ?? '');
    return /[",\n\r]/.test(text) ? `"${text.replaceAll('"', '""')}"` : text;
}

function downloadMerged() {
    if (!state.result) return;
    const format = document.querySelector('#exportFormat').value;
    let content;
    let type;
    let extension;

    if (format === 'json') {
        content = JSON.stringify(Object.fromEntries(state.result.merged.map(row => [row.key, row.value])), null, 2);
        type = 'application/json';
        extension = 'json';
    } else if (format === 'csv') {
        content = ['key,value', ...state.result.merged.map(row => `${csvEscape(row.key)},${csvEscape(row.value)}`)].join('\n');
        type = 'text/csv';
        extension = 'csv';
    } else {
        content = state.result.merged.map(row => `${row.key}=${row.value}`).join('\n');
        type = 'text/plain';
        extension = 'properties';
    }

    const url = URL.createObjectURL(new Blob([content], { type: `${type};charset=utf-8` }));
    const link = document.createElement('a');
    link.href = url;
    link.download = `keyflow-merged.${extension}`;
    link.click();
    URL.revokeObjectURL(url);
}

function clearApp() {
    state.result = null;
    state.files = [null, null];
    elements.form.reset();
    elements.prefix.value = 'eventmessage';
    [1, 2].forEach(number => {
        document.querySelector(`#file${number}Card`).classList.remove('active');
        document.querySelector(`#file${number}Name`).textContent = 'Drop or choose a file';
        document.querySelector(`#file${number}Meta`).textContent = number === 1 ? 'Your original or baseline copy' : 'Your updated or comparison copy';
    });
    elements.error.hidden = true;
    elements.results.hidden = true;
    elements.empty.hidden = false;
}

elements.form.addEventListener('submit', async event => {
    event.preventDefault();
    if (!state.files[0] || !state.files[1]) {
        elements.error.textContent = 'Choose both files before comparing.';
        elements.error.hidden = false;
        return;
    }

    try {
        const [firstContent, secondContent] = await Promise.all(state.files.map(file => file.text()));
        const first = parseMessages(firstContent, elements.prefix.value);
        const second = parseMessages(secondContent, elements.prefix.value);
        if (!first.size && !second.size) throw new Error('No matching key-value entries were found. Check the key prefix or file format.');
        elements.error.hidden = true;
        renderResult(compare(first, second));
    } catch (error) {
        elements.error.textContent = error.message || 'The files could not be compared.';
        elements.error.hidden = false;
    }
});

bindFilePicker(0);
bindFilePicker(1);
document.querySelectorAll('[data-target]').forEach(control => control.addEventListener('click', () => activateSection(control.dataset.target)));
document.querySelector('#downloadMerged').addEventListener('click', downloadMerged);
document.querySelector('#clearButton').addEventListener('click', clearApp);

