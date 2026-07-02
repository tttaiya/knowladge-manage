<template>
  <article class="markdown-preview" v-html="html"></article>
</template>

<script setup>
import { computed } from 'vue';

const props = defineProps({
  content: {
    type: String,
    default: ''
  }
});

const html = computed(() => renderMarkdown(props.content || ''));

function renderMarkdown(markdown) {
  const lines = markdown.split(/\r?\n/);
  const output = [];
  let table = [];

  const flushTable = () => {
    if (!table.length) return;
    output.push(renderTable(table));
    table = [];
  };

  lines.forEach((line) => {
    if (line.trim().startsWith('|') && line.includes('|')) {
      table.push(line);
      return;
    }
    flushTable();
    if (!line.trim()) return;
    if (line.startsWith('### ')) {
      output.push(`<h3>${escapeHtml(line.slice(4))}</h3>`);
    } else if (line.startsWith('## ')) {
      output.push(`<h2>${escapeHtml(line.slice(3))}</h2>`);
    } else if (line.startsWith('# ')) {
      output.push(`<h1>${escapeHtml(line.slice(2))}</h1>`);
    } else {
      output.push(`<p>${renderInline(line)}</p>`);
    }
  });
  flushTable();
  return output.join('');
}

function renderTable(lines) {
  const rows = lines.filter((line) => !/^\s*\|?\s*:?-{3,}:?\s*(\|\s*:?-{3,}:?\s*)+\|?\s*$/.test(line));
  const cells = rows.map((line) => line.split('|').map((cell) => cell.trim()).filter(Boolean));
  const head = cells.shift() || [];
  return `<table><thead><tr>${head.map((cell) => `<th>${escapeHtml(cell)}</th>`).join('')}</tr></thead><tbody>${cells
    .map((row) => `<tr>${row.map((cell) => `<td>${renderInline(cell)}</td>`).join('')}</tr>`)
    .join('')}</tbody></table>`;
}

function renderInline(text) {
  return escapeHtml(text).replace(/!\[([^\]]*)\]\(([^)]+)\)/g, (_match, alt, url) => {
    const safeUrl = String(url).replace(/[<>"']/g, '');
    return `<img src="${safeUrl}" alt="${escapeHtml(alt)}" />`;
  });
}

function escapeHtml(value) {
  return String(value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;');
}
</script>

