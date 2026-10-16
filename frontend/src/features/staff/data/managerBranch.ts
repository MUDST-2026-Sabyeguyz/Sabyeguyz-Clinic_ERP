import { ref } from 'vue'

export type ManagerBranch = {
  code: string
  name: string
  address?: string
}

// Branches are populated from the same master data the clinical screens use.
// Keeping the selected code here lets the Manager header scope every shared
// Therapist screen without changing the Therapist experience.
export const managerBranches = ref<ManagerBranch[]>([])

const storageKey = 'managerSelectedBranch'
const selectedManagerBranchCode = ref(localStorage.getItem(storageKey) ?? '')

export function selectedManagerBranch() {
  const code = selectedManagerBranchCode.value
  return managerBranches.value.find((branch) => branch.code === code) ?? null
}

export function setSelectedManagerBranch(branch: ManagerBranch) {
  selectedManagerBranchCode.value = branch.code
  localStorage.setItem(storageKey, branch.code)
}

export function clearSelectedManagerBranch() {
  selectedManagerBranchCode.value = ''
  localStorage.removeItem(storageKey)
}

export function setManagerBranches(branches: ManagerBranch[]) {
  managerBranches.value = branches
  const selected = selectedManagerBranch()
  if (!selected && branches[0]) setSelectedManagerBranch(branches[0])
}
