interface NamedItem {
  templateName?: string
}

export function appendDuplicateSuffix(baseName: string, existingNames: string[] = []): string {
  const trimmedBaseName = String(baseName || '').trim()
  if (!trimmedBaseName) return ''

  const normalizedNames = existingNames
    .map((name) => String(name || '').trim())
    .filter(Boolean)

  if (!normalizedNames.includes(trimmedBaseName)) {
    return trimmedBaseName
  }

  let suffix = 2
  while (normalizedNames.includes(`${trimmedBaseName}(${suffix})`)) {
    suffix += 1
  }
  return `${trimmedBaseName}(${suffix})`
}

export function buildNumberedDisplayNames<T extends NamedItem>(
  items: T[] = [],
  getName: (item: T) => string | undefined = (item) => item.templateName,
): string[] {
  const normalizedNames = items.map((item) => String(getName(item) || '').trim())
  const totals = new Map<string, number>()
  for (const name of normalizedNames) {
    if (!name) continue
    totals.set(name, (totals.get(name) || 0) + 1)
  }

  const used = new Map<string, number>()
  return normalizedNames.map((name) => {
    if (!name) return ''
    if ((totals.get(name) || 0) <= 1) return name
    const nextIndex = (used.get(name) || 0) + 1
    used.set(name, nextIndex)
    return `${name}(${nextIndex})`
  })
}
