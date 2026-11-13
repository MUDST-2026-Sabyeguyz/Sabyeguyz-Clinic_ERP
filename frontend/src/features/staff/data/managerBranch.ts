import { ref } from 'vue'

export type ManagerBranch = {
  code: string
  name: string
  address?: string
}

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

export function setManagerBranches(
  branches: Array<ManagerBranch | { branchCode: string; name: string; address?: string }>,
) {
  managerBranches.value = branches.map((branch) =>
    'branchCode' in branch
      ? { code: branch.branchCode, name: branch.name, address: branch.address }
      : branch,
  )
  const selected = selectedManagerBranch()
  if (!selected && managerBranches.value[0]) setSelectedManagerBranch(managerBranches.value[0])
}
