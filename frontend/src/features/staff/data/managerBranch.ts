export type ManagerBranch = {
  code: string
  name: string
  address: string
}

export const managerBranches: ManagerBranch[] = [
  { code: 'R9', name: 'พระราม 9', address: '591 ซอยพระรามเก้า 49 แขวงสวนหลวง' },
  { code: 'BR', name: 'แบริ่ง', address: '215 ซอยแบริ่ง 4 ตำบลสำโรงเหนือ สมุทรปราการ 10270' },
]

const storageKey = 'managerSelectedBranch'

export function selectedManagerBranch() {
  const code = localStorage.getItem(storageKey)
  return managerBranches.find((branch) => branch.code === code) ?? null
}

export function setSelectedManagerBranch(branch: ManagerBranch) {
  localStorage.setItem(storageKey, branch.code)
}

export function clearSelectedManagerBranch() {
  localStorage.removeItem(storageKey)
}
