import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import BackOfficeView from '../views/BackOfficeView.vue'
import FilterDropdown from '@/features/staff/components/FilterDropdown.vue'

const branch = {
  id: 1,
  branchCode: '01',
  name: 'สาขาทดสอบ',
  address: 'Bangkok',
  phone: '0812345678',
  active: true,
}
let wrapper: VueWrapper
let requests: Array<{ url: string; method: string; body?: Record<string, unknown> }>
let failedPath: string | undefined
const fixtures: Record<string, unknown> = {
  '/api/branches': [branch],
  '/api/users': [
    {
      id: 2,
      username: 'alice',
      email: 'alice@test.com',
      employeeCode: 'E01',
      phone: '0812345678',
      fullName: 'Alice',
      role: 'THERAPIST',
      branchCode: '01',
      branchName: 'สาขาทดสอบ',
      active: true,
    },
  ],
  '/api/services': [
    { id: 3, code: 'S01', name: 'Treatment', price: 500, defaultDurationMinutes: 30, active: true },
  ],
  '/api/courses': [
    {
      id: 4,
      code: 'C01',
      name: 'Course ten',
      price: 4000,
      sessions: 10,
      bonusSessions: 2,
      validityDays: 365,
      active: true,
    },
  ],
  '/api/payment-methods': [
    { id: 4, code: 'CASH', name: 'Cash', active: true },
    { id: 5, code: 'BANK', name: 'BankTransfer', active: true },
    { id: 6, code: 'PROMPTPAY', name: 'PromptPay', active: false },
  ],
  '/api/resources': [
    {
      id: 6,
      branchCode: '01',
      branchName: 'สาขาทดสอบ',
      code: 'R01',
      name: 'Room one',
      type: 'ROOM',
      status: 'AVAILABLE',
      active: true,
    },
  ],
  '/api/master-data': [
    { id: 7, category: 'Customer Group', code: 'M01', name: 'VIP', active: true },
  ],
  '/api/commission/schemes': [],
}
beforeEach(() => {
  requests = []
  failedPath = undefined
  vi.spyOn(window, 'confirm').mockReturnValue(true)
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string, init?: RequestInit) => {
      const method = init?.method ?? 'GET'
      const body = init?.body ? JSON.parse(String(init.body)) : undefined
      requests.push({ url, method, body })
      if (url === failedPath)
        return {
          ok: false,
          status: 500,
          text: async () => JSON.stringify({ message: 'Backend unavailable' }),
        }
      const collection = url.replace(/\/\d+(\/activate)?$/, '')
      const rows = fixtures[collection] as Array<Record<string, unknown>> | undefined
      const result =
        method === 'GET'
          ? (fixtures[url] ?? [])
          : { ...rows?.[0], ...body, id: rows?.[0]?.id ?? 10, active: method !== 'DELETE' }
      return { ok: true, text: async () => JSON.stringify(result) }
    }),
  )
})
afterEach(() => {
  wrapper?.unmount()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})
async function start() {
  wrapper = mount(BackOfficeView, { global: { stubs: { StaffHeader: true } } })
  await flushPromises()
}
async function section(label: string) {
  await wrapper
    .findAll('.settings-nav button')
    .find((button) => button.text() === label)!
    .trigger('click')
}
async function field(label: string, value: string) {
  const input = wrapper
    .findAll('.drawer label')
    .find((item) => item.text().startsWith(label))!
    .find('input')
  await input.setValue(value)
}
async function save() {
  await wrapper.get('button.save').trigger('click')
  await flushPromises()
}

describe('BackOffice backend integration', () => {
  it('hydrates the current layout from backend collections', async () => {
    await start()
    expect(wrapper.get('tbody').text()).toContain('สาขาทดสอบ')
    await section('บริการและคอร์ส')
    expect(wrapper.get('tbody').text()).toContain('Course ten')
    expect(wrapper.get('tbody').text()).toContain('Treatment')
    await section('Master Data')
    expect(wrapper.get('tbody').text()).toContain('VIP')
  })
  it('shows load errors persistently while rendering successful collections', async () => {
    failedPath = '/api/users'
    await start()
    expect(wrapper.get('tbody').text()).toContain('สาขาทดสอบ')
    expect(wrapper.get('[role="alert"]').text()).toContain('ผู้ใช้งาน')
  })
  it('has no close button in the user table: the status is set in the edit form', async () => {
    await start()
    await section('ผู้ใช้และสิทธิ์')
    expect(wrapper.findAll('tbody button')).toHaveLength(1)
    expect(wrapper.get('tbody button').text()).toBe('แก้ไข')
    await wrapper.get('button.edit').trigger('click')
    wrapper.findAllComponents(FilterDropdown).find((item) => item.props('label') === 'สถานะ')!.vm.$emit('update:modelValue', 'ปิดใช้งาน')
    await save()
    expect(requests).toContainEqual({ url: '/api/users/2', method: 'DELETE', body: undefined })
    await wrapper.get('button.edit').trigger('click')
    wrapper.findAllComponents(FilterDropdown).find((item) => item.props('label') === 'สถานะ')!.vm.$emit('update:modelValue', 'ใช้งาน')
    await save()
    expect(requests).toContainEqual({ url: '/api/users/2/activate', method: 'PATCH', body: {} })
  })
  it('shows read-only permissions derived from the selected role', async () => {
    await start()
    await section('ผู้ใช้และสิทธิ์')
    await wrapper.get('button.edit').trigger('click')
    expect(wrapper.get('.drawer fieldset').attributes('disabled')).toBeDefined()
    expect(wrapper.get('.drawer legend').text()).toBe('สิทธิ์ตามบทบาท')
    const selected = () =>
      wrapper
        .findAll('.drawer input[type="checkbox"]')
        .filter((input) => (input.element as HTMLInputElement).checked)
        .map((input) => (input.element as HTMLInputElement).value)
    expect(selected()).toEqual(['Patient', 'Visit', 'Treatment'])
    await wrapper.get('button[aria-label="Role"]').trigger('click')
    await wrapper
      .findAll('[role="option"]')
      .find((option) => option.text().includes('Manager'))!
      .trigger('click')
    expect(selected()).toEqual([
      'Patient',
      'Visit',
      'Treatment',
      'Course',
      'Transaction',
      'Commission',
    ])
    expect(wrapper.get('.drawer fieldset').attributes('disabled')).toBeDefined()
    await wrapper.get('button[aria-label="Role"]').trigger('click')
    await wrapper
      .findAll('[role="option"]')
      .find((option) => option.text().includes('Therapist'))!
      .trigger('click')
    expect(selected()).toEqual(['Patient', 'Visit', 'Treatment'])
  })
  describe('treatment fee rules (กฎค่ามือ)', () => {
    // the rule in force, and (in some tests) the one waiting to start
    const rule = {
      id: 3,
      feeType: 'Fixed',
      feeValue: 300,
      effectiveFrom: '2020-01-01',
      effectiveTo: null,
      enabled: true,
      used: false,
      name: 'ค่ามือ — บริการรายครั้ง',
    }
    const pending = { ...rule, id: 4, feeValue: 350, effectiveFrom: '2999-01-01', name: 'ค่ามือใหม่' }
    beforeEach(() => {
      fixtures['/api/commission/treatment-fees'] = [rule]
    })
    afterEach(() => {
      delete fixtures['/api/commission/treatment-fees']
    })
    const open = async () => {
      await start()
      await section('กฎค่ามือ')
    }
    const edit = async () => {
      await wrapper.get('.settings-panel-head button.settings-add').trigger('click')
    }
    const pickType = async (value: 'Fixed' | 'Percentage') => {
      await wrapper.get(`.drawer input[type="radio"][value="${value}"]`).setValue()
    }
    const valueInput = () => wrapper.get('.drawer input[type="number"]')
    const monthInput = () => wrapper.get('.drawer input[type="month"]')

    it('shows the rule in force as a version, worded for the treatment fee, with who recorded it last and when', async () => {
      fixtures['/api/commission/treatment-fees'] = [{ ...rule, lastEditedBy: 'Manager Demo', lastEditedAt: '2026-10-16T08:46:24Z' }]
      await open()
      expect(wrapper.get('.settings-panel-head h2').text()).toBe('กฎค่ามือ')
      expect(wrapper.get('.settings-panel-head p').text()).toContain('ผู้รักษาแทน')
      const card = wrapper.get('.fee-version')
      expect(card.text()).toContain('รายละเอียดเวอร์ชัน V1')
      expect(card.get('.version-badge').text()).toBe('ใช้อยู่')
      expect(card.text()).toContain('มีผลตั้งแต่รอบเดือน')
      expect(card.findAll('thead th').map((th) => th.text())).toEqual([
        'ชื่อกฎ', 'ผู้ได้รับค่ามือ', 'วิธีคำนวณ', 'ผู้บันทึกล่าสุด', 'แก้ไขล่าสุด',
      ])
      const cells = card.findAll('tbody td').map((td) => td.text())
      expect(cells[0]).toBe('ค่ามือ — บริการรายครั้ง')
      expect(cells[1]).toBe('นักกายภาพทั้งหมด')
      expect(cells[2]).toBe('300 บาท / ครั้ง')
      expect(cells[3]).toBe('Manager Demo')
      expect(cells[4]).toContain('2569')
      // no switch and no edit button per row: a version's state comes from its dates
      expect(wrapper.find('.fee-status').exists()).toBe(false)
      expect(wrapper.find('button.edit').exists()).toBe(false)
      expect(wrapper.get('.settings-panel-head').text()).toContain('ประวัติเวอร์ชัน')
      expect(wrapper.get('.settings-panel-head').text()).toContain('แก้กฎค่ามือ')
    })

    it('shows a dash when a rule has no editor or edit time yet', async () => {
      await open()
      const cells = wrapper.get('.fee-version').findAll('tbody td').map((td) => td.text())
      expect(cells[3]).toBe('-')
      expect(cells[4]).toBe('-')
    })

    it('shows the rule waiting to start after the one in force', async () => {
      fixtures['/api/commission/treatment-fees'] = [rule, pending]
      await open()
      const cards = wrapper.findAll('.fee-version')
      expect(cards).toHaveLength(2)
      expect(cards[0]!.text()).toContain('รายละเอียดเวอร์ชัน V1')
      expect(cards[0]!.get('.version-badge').text()).toBe('ใช้อยู่')
      expect(cards[1]!.text()).toContain('เวอร์ชันที่รอเริ่มใช้ V2')
      expect(cards[1]!.get('.version-badge').text()).toBe('รอเริ่มใช้')
      expect(cards[1]!.text()).toContain('350 บาท / ครั้ง')
    })

    it('says so when no rule is in force', async () => {
      fixtures['/api/commission/treatment-fees'] = [pending]
      await open()
      expect(wrapper.get('.fee-versions').text()).toContain('ยังไม่มีกฎค่ามือที่ใช้อยู่')
    })

    it('shows the version in force, with its tiers, on the กฎค่าคอมมิชชัน page and not one that has not started', async () => {
      fixtures['/api/commission/schemes'] = [
        { id: 6, name: 'S', version: 4, effectiveFrom: '2999-02-01', effectiveTo: null, locked: false },
        { id: 5, name: 'S', version: 3, effectiveFrom: '2020-02-01', effectiveTo: '2998-12-31', locked: true },
        { id: 4, name: 'S', version: 2, effectiveFrom: '2020-01-01', effectiveTo: '2020-01-31', locked: true },
      ]
      fixtures['/api/commission/schemes/5/tiers'] = [
        { id: 1, order: 1, minimum: 0, maximum: 59999.99, rate: 0.05, active: true },
        { id: 2, order: 2, minimum: 60000, maximum: null, rate: 0.12, active: true },
      ]
      await start()
      await section('กฎค่าคอมมิชชัน')
      const detail = wrapper.get('.latest-version')
      expect(detail.text()).toContain('รายละเอียดเวอร์ชัน V3') // V4 is newer but waits to start, V2 has ended
      expect(detail.get('.version-badge').text()).toBe('ใช้อยู่')
      expect(detail.text()).toContain('มีผลสำหรับรอบเดือน')
      expect(detail.findAll('thead th').map((th) => th.text())).toEqual([
        'Tier', 'ยอดขายขั้นต่ำ (บาท)', 'ยอดขายสูงสุด (บาท)', 'คอมมิชชัน',
      ])
      expect(detail.findAll('tbody tr')).toHaveLength(2)
      expect(detail.text()).toContain('59,999.99')
      expect(detail.text()).toContain('ไม่จำกัด')
      expect(detail.text()).toContain('12%')
      expect(wrapper.find('.settings-panel > .table-wrap').exists()).toBe(false) // the old table is gone
      expect(wrapper.find('button.settings-add').exists()).toBe(false) // tiers are not added here
      expect(wrapper.get('.settings-panel-head').text()).toContain('ตั้งค่า Tier')
      expect(wrapper.get('.settings-panel-head').text()).toContain('ประวัติเวอร์ชัน')
      delete fixtures['/api/commission/schemes/5/tiers']
      fixtures['/api/commission/schemes'] = []
    })

    it('says so when no version is in force', async () => {
      await start()
      await section('กฎค่าคอมมิชชัน')
      expect(wrapper.get('.latest-version').text()).toContain('ยังไม่มีเวอร์ชัน')
      fixtures['/api/commission/schemes'] = [
        { id: 6, name: 'S', version: 4, effectiveFrom: '2999-02-01', effectiveTo: null, locked: false },
      ]
      wrapper.unmount()
      await start()
      await section('กฎค่าคอมมิชชัน')
      expect(wrapper.get('.latest-version').text()).toContain('ยังไม่มีเวอร์ชัน') // only a pending version exists
      fixtures['/api/commission/schemes'] = []
    })

    it('opens the form with the values of the rule in force and saves them as a new rule for a later month', async () => {
      await open()
      await edit()
      expect(wrapper.get('.drawer h2').text()).toContain('บันทึกเป็นเวอร์ชันใหม่')
      expect((wrapper.get('.drawer input[maxlength="100"]').element as HTMLInputElement).value).toBe('ค่ามือ — บริการรายครั้ง')
      expect((valueInput().element as HTMLInputElement).value).toBe('300')
      // only a start month: the 1st, after the current month; the end is the system's
      expect((monthInput().element as HTMLInputElement).value).toMatch(/^[0-9]{4}-[0-9]{2}$/)
      expect(monthInput().attributes('min')).toBe((monthInput().element as HTMLInputElement).value)
      expect(wrapper.get('.drawer').text()).not.toContain('สิ้นสุด (เว้นว่างได้)')
      expect(wrapper.get('.drawer').text()).not.toContain('สถานะ')
      await valueInput().setValue('350')
      await save()
      const post = requests.find((r) => r.method === 'POST' && r.url === '/api/commission/treatment-fees')
      expect(post?.body).toMatchObject({ name: 'ค่ามือ — บริการรายครั้ง', feeType: 'Fixed', feeValue: 350 })
      expect(post?.body?.effectiveFrom).toMatch(/^[0-9]{4}-[0-9]{2}-01$/)
      expect(post?.body).not.toHaveProperty('effectiveTo')
      expect(requests.some((r) => r.url.endsWith('/enable') || r.url.endsWith('/disable'))).toBe(false)
    })

    it('edits the version waiting to start instead of making another one, month included', async () => {
      fixtures['/api/commission/treatment-fees'] = [rule, pending]
      await open()
      await edit()
      expect(wrapper.get('.drawer h2').text()).toContain('แก้ไขเวอร์ชันที่รอเริ่มใช้')
      expect((monthInput().element as HTMLInputElement).value).toBe('2999-01')
      await monthInput().setValue('2999-02')
      await valueInput().setValue('400')
      await save()
      expect(requests.some((r) => r.method === 'POST')).toBe(false)
      const put = requests.find((r) => r.method === 'PUT' && r.url === '/api/commission/treatment-fees/4')
      expect(put?.body).toMatchObject({ feeValue: 400, effectiveFrom: '2999-02-01' })
      expect(put?.body).not.toHaveProperty('effectiveTo')
    })

    it('saves a rule paid as a percentage', async () => {
      await open()
      await edit()
      await pickType('Percentage')
      await valueInput().setValue('12.5')
      await save()
      const post = requests.find((r) => r.method === 'POST' && r.url === '/api/commission/treatment-fees')
      expect(post?.body).toMatchObject({ feeType: 'Percentage', feeValue: 12.5 })
    })

    it('refuses a percentage above 100 and a missing value before calling the server', async () => {
      await open()
      await edit()
      await pickType('Percentage')
      await valueInput().setValue('150')
      await save()
      expect(wrapper.get('.validation-message').text()).toContain('0 ถึง 100')
      await valueInput().setValue('')
      await save()
      expect(requests.some((r) => r.method === 'POST')).toBe(false)
    })

    it('locks the type, the value and the start of a rule already used, but not its name', async () => {
      fixtures['/api/commission/treatment-fees'] = [rule, { ...pending, used: true }]
      await open()
      await edit()
      expect(wrapper.get('.drawer fieldset.fee-type').attributes('disabled')).toBeDefined()
      expect(valueInput().attributes('disabled')).toBeDefined()
      expect(monthInput().attributes('disabled')).toBeDefined()
      expect(wrapper.get('.drawer input[maxlength="100"]').attributes('disabled')).toBeUndefined()
    })
  })
  it('opens the category named in the address, and the first one otherwise', async () => {
    window.history.pushState({}, '', '/staff/settings?section=tiers')
    await start()
    expect(wrapper.get('.settings-nav button.active').text()).toBe('กฎค่าคอมมิชชัน')
    wrapper.unmount()
    window.history.pushState({}, '', '/staff/settings?section=nope')
    await start()
    expect(wrapper.get('.settings-nav button.active').text()).toBe('สาขา')
    window.history.pushState({}, '', '/')
  })
  it('creates a branch using its two digit code, required address and phone', async () => {
    await start()
    await wrapper.get('.settings-add').trigger('click')
    await field('รหัสสาขา', '02')
    await field('ชื่อสาขา', 'New branch')
    await field('ที่อยู่', 'Chiang Mai')
    await field('ข้อมูลติดต่อ', '0891234567')
    await save()
    expect(requests).toContainEqual({
      url: '/api/branches',
      method: 'POST',
      body: { branchCode: '02', name: 'New branch', address: 'Chiang Mai', phone: '0891234567' },
    })
    expect(wrapper.get('tbody').text()).toContain('New branch')
    expect(wrapper.find('.drawer').exists()).toBe(false)
  })
  it('updates the persisted branch and keeps its code immutable', async () => {
    await start()
    await wrapper.get('button.edit').trigger('click')
    expect(wrapper.findAll('.drawer input')[0]!.attributes('disabled')).toBeDefined()
    await field('ชื่อสาขา', 'Updated branch')
    await save()
    expect(requests).toContainEqual({
      url: '/api/branches/1',
      method: 'PUT',
      body: { branchCode: '01', name: 'Updated branch', address: 'Bangkok', phone: '0812345678' },
    })
    expect(wrapper.get('tbody').text()).toContain('Updated branch')
  })
  it('sets the status of a branch when editing it: closing and opening again show red and green', async () => {
    await start()
    expect(wrapper.get('tbody .settings-status').classes()).not.toContain('status-off')
    await wrapper.get('button.edit').trigger('click')
    const status = wrapper.getComponent(FilterDropdown)
    expect(status.props('options').map((item: { value: string }) => item.value)).toEqual(['ใช้งาน', 'ปิดใช้งาน'])
    status.vm.$emit('update:modelValue', 'ปิดใช้งาน')
    await save()
    expect(requests).toContainEqual({ url: '/api/branches/1', method: 'DELETE', body: undefined })
    expect(wrapper.get('tbody .settings-status').text()).toBe('ปิดใช้งาน')
    expect(wrapper.get('tbody .settings-status').classes()).toContain('status-off')
    await wrapper.get('button.edit').trigger('click')
    wrapper.getComponent(FilterDropdown).vm.$emit('update:modelValue', 'ใช้งาน')
    await save()
    expect(requests).toContainEqual({ url: '/api/branches/1/activate', method: 'PATCH', body: {} })
    expect(wrapper.get('tbody .settings-status').text()).toBe('ใช้งาน')
    expect(wrapper.get('tbody .settings-status').classes()).not.toContain('status-off')
  })
  it('does not offer a closed branch when adding a room, a resource or a user', async () => {
    fixtures['/api/branches'] = [branch, { ...branch, id: 2, branchCode: '02', name: 'Closed branch', active: false }]
    await start()
    await section('Room / Resource')
    await wrapper.get('.settings-add').trigger('click')
    const branchDropdown = wrapper.findAllComponents(FilterDropdown).find((item) => item.props('label') === 'สาขา')!
    expect(branchDropdown.props('options').map((item: { value: string }) => item.value)).toEqual(['สาขาทดสอบ'])
    fixtures['/api/branches'] = [branch]
  })
  it('sets the status of a user when editing it, red and green, and offers none when adding', async () => {
    await start()
    await section('ผู้ใช้และสิทธิ์')
    await wrapper.get('.settings-add').trigger('click')
    expect(wrapper.findAllComponents(FilterDropdown).some((item) => item.props('label') === 'สถานะ')).toBe(false)
    await wrapper.get('.drawer header button').trigger('click')
    await wrapper.findAll('button.edit')[0]!.trigger('click')
    const status = () => wrapper.findAllComponents(FilterDropdown).find((item) => item.props('label') === 'สถานะ')!
    expect(wrapper.get('.drawer label.status-field').classes()).toContain('status-on')
    status().vm.$emit('update:modelValue', 'ปิดใช้งาน')
    await flushPromises()
    expect(wrapper.get('.drawer label.status-field').classes()).toContain('status-off')
    await save()
    expect(requests).toContainEqual({ url: '/api/users/2', method: 'DELETE', body: undefined })
    expect(wrapper.get('tbody .settings-status').classes()).toContain('status-off')
  })
  it('sets the status of a master data item when editing it, and offers none when adding', async () => {
    await start()
    await section('Master Data')
    const statusField = () => wrapper.findAllComponents(FilterDropdown).find((item) => item.props('label') === 'สถานะ')
    await wrapper.get('.settings-add').trigger('click')
    expect(statusField()).toBeUndefined()
    await wrapper.get('.drawer header button').trigger('click')
    await wrapper.get('button.edit').trigger('click')
    expect(wrapper.get('.drawer label.status-field').classes()).toContain('status-on')
    statusField()!.vm.$emit('update:modelValue', 'ปิดใช้งาน')
    await flushPromises()
    expect(wrapper.get('.drawer label.status-field').classes()).toContain('status-off')
    await save()
    expect(requests.some((r) => r.method === 'DELETE' && r.url.startsWith('/api/master-data/'))).toBe(true)
    expect(wrapper.get('tbody .settings-status').classes()).toContain('status-off')
  })
  it('shows the status of a room as in the dictionary: ใช้งาน green, ปิดใช้งาน red, and saves it', async () => {
    const original = fixtures['/api/resources']
    fixtures['/api/resources'] = [
      { id: 6, branchCode: '01', branchName: 'สาขาทดสอบ', code: 'R1', name: 'Room A', type: 'ROOM', status: 'ACTIVE', active: true },
      { id: 7, branchCode: '01', branchName: 'สาขาทดสอบ', code: 'R2', name: 'Room B', type: 'ROOM', status: 'INACTIVE', active: false },
    ]
    await start()
    await section('Room / Resource')
    const badges = wrapper.findAll('tbody .settings-status')
    expect(badges.map((item) => item.text())).toEqual(['ใช้งาน', 'ปิดใช้งาน'])
    expect(badges[0]!.classes()).not.toContain('status-off')
    expect(badges[1]!.classes()).toContain('status-off')
    await wrapper.findAll('button.edit')[0]!.trigger('click')
    const dropdown = wrapper.findAllComponents(FilterDropdown).find((item) => item.props('label') === 'สถานะ')!
    expect(dropdown.props('options').map((item: { value: string }) => item.value)).toEqual(['ใช้งาน', 'ปิดใช้งาน'])
    expect(wrapper.get('.drawer label.status-field').classes()).toContain('status-on')
    dropdown.vm.$emit('update:modelValue', 'ปิดใช้งาน')
    await flushPromises()
    expect(wrapper.get('.drawer label.status-field').classes()).toContain('status-off')
    await save()
    const put = requests.find((r) => r.method === 'PUT' && r.url === '/api/resources/6')
    expect(put?.body).toMatchObject({ status: 'INACTIVE' })
    fixtures['/api/resources'] = original
  })
  it('adds a branch that is open without offering a status to choose', async () => {
    await start()
    await wrapper.get('.settings-add').trigger('click')
    expect(wrapper.findComponent(FilterDropdown).exists()).toBe(false)
    await field('รหัสสาขา', '02')
    await field('ชื่อสาขา', 'New branch')
    await field('ที่อยู่', 'Chiang Mai')
    await field('ข้อมูลติดต่อ', '0891234567')
    await save()
    expect(requests.some((r) => r.url.includes('/activate') || r.method === 'DELETE')).toBe(false)
    expect(wrapper.get('tbody').text()).toContain('New branch')
    expect(wrapper.findAll('tbody .settings-status').at(-1)!.classes()).not.toContain('status-off')
  })
  it('preserves phone validation and rejects invalid branch codes before sending', async () => {
    await start()
    await wrapper.get('.settings-add').trigger('click')
    await field('รหัสสาขา', 'XX')
    await field('ชื่อสาขา', 'Invalid')
    await field('ที่อยู่', 'Bangkok')
    await field('ข้อมูลติดต่อ', '123')
    await save()
    expect(wrapper.findAll('.drawer .invalid').length).toBeGreaterThan(0)
    expect(requests.filter((request) => request.method === 'POST')).toHaveLength(0)
  })
  it('keeps failed branch updates open without displaying a successful save', async () => {
    await start()
    failedPath = '/api/branches/1'
    await wrapper.get('button.edit').trigger('click')
    await field('ชื่อสาขา', 'Failed edit')
    await save()
    expect(wrapper.get('.drawer').text()).toContain('Backend unavailable')
    expect(wrapper.get('tbody').text()).not.toContain('Failed edit')
    expect(wrapper.find('.save-success-modal').exists()).toBe(false)
  })
  it('has no bonus field: the total sessions is the one number, and validity days are editable', async () => {
    await start()
    await section('บริการและคอร์ส')
    await wrapper.findAll('button.edit')[1]!.trigger('click')
    expect(wrapper.get('.drawer').text()).not.toContain('Bonus')
    await field('อายุคอร์ส', '90')
    await save()
    expect(requests).toContainEqual({
      url: '/api/courses/4',
      method: 'PUT',
      body: {
        code: 'C01',
        name: 'Course ten',
        price: 4000,
        sessions: 12,
        bonusSessions: 0,
        validityDays: 90,
        validityValue: 90,
        validityUnit: 'Day',
      },
    })
  })
  describe('services and courses form', () => {
    const inputOf = (label: string) =>
      wrapper.findAll('.drawer label').find((item) => item.text().startsWith(label))!.get('input')
    const dropdowns = () => wrapper.findAllComponents(FilterDropdown)
    const openServices = async () => {
      await start()
      await section('บริการและคอร์ส')
    }

    it('starts the add form empty: no default number is filled in', async () => {
      await openServices()
      await wrapper.get('.settings-add').trigger('click')
      dropdowns()[0]!.vm.$emit('update:modelValue', 'Course')
      await flushPromises()
      expect((inputOf('ชื่อคอร์ส').element as HTMLInputElement).value).toBe('')
      expect((inputOf('ราคา').element as HTMLInputElement).value).toBe('')
      expect((inputOf('จำนวนครั้งรวม').element as HTMLInputElement).value).toBe('')
      expect((inputOf('อายุคอร์ส').element as HTMLInputElement).value).toBe('')
      // there is no status to choose for a new item
      expect(dropdowns()).toHaveLength(1)
      await save()
      expect(wrapper.findAll('.drawer .invalid').length).toBeGreaterThan(0)
      expect(requests.some((r) => r.method === 'POST')).toBe(false)
    })

    it('sets the status of a course and of a single visit when editing, shown red and green', async () => {
      await openServices()
      const badge = (i: number) => wrapper.findAll('tbody .settings-status')[i]!
      expect(badge(1).classes()).not.toContain('status-off')
      await wrapper.findAll('button.edit')[1]!.trigger('click')
      expect(dropdowns()[1]!.props('options').map((item: { value: string }) => item.value)).toEqual(['ใช้งาน', 'ปิดใช้งาน'])
      dropdowns()[1]!.vm.$emit('update:modelValue', 'ปิดใช้งาน')
      await save()
      expect(requests).toContainEqual({ url: '/api/services/4', method: 'DELETE', body: undefined })
      expect(badge(1).text()).toBe('ปิดใช้งาน')
      expect(badge(1).classes()).toContain('status-off')
      await wrapper.findAll('button.edit')[0]!.trigger('click')
      dropdowns()[1]!.vm.$emit('update:modelValue', 'ปิดใช้งาน')
      await save()
      expect(requests).toContainEqual({ url: '/api/services/3', method: 'DELETE', body: undefined })
      expect(badge(0).classes()).toContain('status-off')
      await wrapper.findAll('button.edit')[0]!.trigger('click')
      dropdowns()[1]!.vm.$emit('update:modelValue', 'ใช้งาน')
      await save()
      expect(requests).toContainEqual({ url: '/api/services/3/activate', method: 'PATCH', body: {} })
      expect(badge(0).classes()).not.toContain('status-off')
    })

    it('lets the type be chosen when editing: a single visit becomes a course, the type is changed first', async () => {
      await openServices()
      await wrapper.findAll('button.edit')[0]!.trigger('click')
      expect(dropdowns()[0]!.props('options').map((item: { value: string }) => item.value)).toEqual(['Single Visit', 'Course'])
      dropdowns()[0]!.vm.$emit('update:modelValue', 'Course')
      await flushPromises()
      // the course fields start empty for the item that was a single visit
      await inputOf('จำนวนครั้งรวม').setValue('8')
      await inputOf('อายุคอร์ส').setValue('3')
      await save()
      const sent = requests.filter((r) => r.method !== 'GET').map((r) => `${r.method} ${r.url}`)
      expect(sent.indexOf('POST /api/services/3/type')).toBeGreaterThanOrEqual(0)
      expect(sent.indexOf('POST /api/services/3/type')).toBeLessThan(sent.indexOf('PUT /api/courses/3'))
      expect(requests).toContainEqual({ url: '/api/services/3/type', method: 'POST', body: { type: 'Course' } })
    })
  })
  it('saves the course validity in months and the optional appointment length', async () => {
    await start()
    await section('บริการและคอร์ส')
    await wrapper.findAll('button.edit')[1]!.trigger('click')
    await field('อายุคอร์ส', '6')
    await wrapper.get('select[aria-label="หน่วยอายุคอร์ส"]').setValue('Month')
    await field('ระยะเวลานัดเริ่มต้น (นาที)', '45')
    await save()
    expect(requests).toContainEqual(
      expect.objectContaining({
        url: '/api/courses/4',
        method: 'PUT',
        body: expect.objectContaining({
          validityValue: 6,
          validityUnit: 'Month',
          defaultDurationMinutes: 45,
        }),
      }),
    )
  })
  it('does not ask a single visit for sessions or an expiry, and the appointment length is optional', async () => {
    await start()
    await section('บริการและคอร์ส')
    await wrapper.get('.settings-add').trigger('click')
    await field('ชื่อคอร์ส', 'Massage')
    await field('ราคา (บาท)', '900')
    expect(wrapper.get('.drawer').text()).not.toContain('อายุคอร์ส')
    expect(wrapper.get('.drawer').text()).not.toContain('จำนวนครั้งรวม')
    await save()
    expect(requests).toContainEqual(
      expect.objectContaining({
        url: '/api/services',
        method: 'POST',
        body: expect.objectContaining({ name: 'Massage', price: 900 }),
      }),
    )
    const created = requests.find((request) => request.url === '/api/services' && request.method === 'POST')
    expect((created?.body as { defaultDurationMinutes?: number }).defaultDurationMinutes).toBeUndefined()
  })
  it('sends the branch booking window, email, licence and default appointment length', async () => {
    await start()
    await wrapper.get('button.edit').trigger('click')
    await field('อีเมลสาขา', 'rama9@clinic.test')
    await field('เลขใบอนุญาต', 'PT-1234')
    await field('เปิดรับนัดตั้งแต่', '09:00')
    await field('ถึงเวลา', '18:00')
    await field('ระยะเวลานัดเริ่มต้น (นาที)', '60')
    await save()
    expect(requests).toContainEqual(
      expect.objectContaining({
        url: '/api/branches/1',
        method: 'PUT',
        body: expect.objectContaining({
          email: 'rama9@clinic.test',
          licenseNo: 'PT-1234',
          bookingOpenTime: '09:00',
          bookingCloseTime: '18:00',
          defaultAppointmentMinutes: 60,
        }),
      }),
    )
  })
  it('rejects a branch booking window with only one end or a wrong order', async () => {
    await start()
    await wrapper.get('button.edit').trigger('click')
    await field('เปิดรับนัดตั้งแต่', '09:00')
    await save()
    expect(wrapper.get('.drawer').text()).toContain('ช่วงเปิดรับนัด')
    await field('ถึงเวลา', '08:00')
    await save()
    expect(wrapper.get('.drawer').text()).toContain('ช่วงเปิดรับนัด')
    expect(requests.filter((request) => request.method === 'PUT')).toHaveLength(0)
  })
  it('sets the status of each payment method after a confirmation; any number can be on', async () => {
    await start()
    await section('การชำระเงิน')
    // there is no cash payment, so it is not listed, and there is nothing to pick
    const rows = () => wrapper.findAll('.payment-option')
    expect(rows()).toHaveLength(2)
    expect(wrapper.text()).not.toContain('Cash')
    expect(wrapper.find('.payment-option .radio').exists()).toBe(false)
    const enable = (i: number) => rows()[i]!.get('button.enable')
    const disable = (i: number) => rows()[i]!.get('button.disable')
    // the real state is given, but no status is coloured until one is chosen here
    expect(enable(0).attributes('aria-pressed')).toBe('true')
    expect(disable(1).attributes('aria-pressed')).toBe('true')
    expect(enable(0).attributes('disabled')).toBeUndefined()
    expect(wrapper.findAll('.payment-status button.on')).toHaveLength(0)
    // every button can be chosen: the status a method already has is confirmed too, calls nothing, and takes its colour
    await enable(0).trigger('click')
    expect(wrapper.get('.confirm-modal').text()).toContain('เปิดใช้งาน (Enable)')
    await wrapper.get('.confirm-ok').trigger('click')
    await flushPromises()
    expect(enable(0).classes()).toContain('on')
    expect(requests.some((r) => r.url.startsWith('/api/payment-methods/'))).toBe(false)
    // cancelling changes nothing
    await enable(1).trigger('click')
    expect(wrapper.get('.confirm-modal').text()).toContain('เปิดใช้งาน (Enable)')
    await wrapper.get('.confirm-cancel').trigger('click')
    expect(requests.some((r) => r.url.startsWith('/api/payment-methods/'))).toBe(false)
    // a failed change keeps the state
    failedPath = '/api/payment-methods/6/activate'
    await enable(1).trigger('click')
    await wrapper.get('.confirm-ok').trigger('click')
    await flushPromises()
    expect(disable(1).attributes('aria-pressed')).toBe('true')
    expect(wrapper.findAll('.payment-status button.on')).toHaveLength(1)
    failedPath = undefined
    // confirming sets only that method, and the chosen status takes its colour (Enable green, Disable red)
    await enable(1).trigger('click')
    await wrapper.get('.confirm-ok').trigger('click')
    await flushPromises()
    expect(enable(1).classes()).toContain('on')
    expect(enable(0).attributes('aria-pressed')).toBe('true')
    expect(requests).toContainEqual({ url: '/api/payment-methods/6/activate', method: 'PATCH', body: {} })
    expect(requests.some((r) => r.method === 'DELETE')).toBe(false)
    await disable(0).trigger('click')
    await wrapper.get('.confirm-ok').trigger('click')
    await flushPromises()
    expect(disable(0).classes()).toContain('on')
    expect(requests).toContainEqual({ url: '/api/payment-methods/5', method: 'DELETE', body: undefined })
  })
  it.each([
    [
      'บริการและคอร์ส',
      0,
      'ชื่อคอร์ส',
      '/api/services/3',
      { code: 'S01', name: 'Updated', price: 500, defaultDurationMinutes: 30 },
    ],
    [
      'บริการและคอร์ส',
      1,
      'ชื่อคอร์ส',
      '/api/courses/4',
      {
        code: 'C01',
        name: 'Updated',
        price: 4000,
        // the 2 bonus sessions of the stored course are counted in the total
        sessions: 12,
        bonusSessions: 0,
        validityDays: 365,
        validityValue: 365,
        validityUnit: 'Day',
      },
    ],
    [
      'Room / Resource',
      0,
      'ชื่อห้อง/ทรัพยากร',
      '/api/resources/6',
      { branchCode: '01', code: 'R01', name: 'Updated', type: 'ROOM', status: 'ACTIVE' },
    ],
    [
      'Master Data',
      0,
      'ตัวอย่างรายการ',
      '/api/master-data/7',
      { category: 'Customer Group', code: 'M01', name: 'Updated' },
    ],
    [
      'ผู้ใช้และสิทธิ์',
      0,
      'ชื่อผู้ใช้งาน',
      '/api/users/2',
      {
        username: 'alice',
        email: 'alice@test.com',
        employeeCode: 'E01',
        phone: '0812345678',
        fullName: 'Updated',
        role: 'THERAPIST',
        branchCode: '01',
      },
    ],
  ])(
    'saves %s edits to the correct backend collection',
    async (label, index, fieldLabel, url, body) => {
      await start()
      await section(String(label))
      await wrapper.findAll('button.edit')[Number(index)]!.trigger('click')
      await field(String(fieldLabel), 'Updated')
      await save()
      expect(requests).toContainEqual({ url, method: 'PUT', body })
      expect(wrapper.get('tbody').text()).toContain('Updated')
    },
  )
})
