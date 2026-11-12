import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import BackOfficeView from '../views/BackOfficeView.vue'

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
  '/api/payment-methods': [{ id: 5, code: 'CASH', name: 'Cash', active: true }],
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
  it('persists user deactivate and reactivate through the existing endpoints', async () => {
    await start()
    await section('ผู้ใช้และสิทธิ์')
    await wrapper.get('.user-toggle').trigger('click')
    await flushPromises()
    expect(wrapper.get('.user-toggle').text()).toBe('เปิดใช้งาน')
    expect(requests).toContainEqual({ url: '/api/users/2', method: 'DELETE', body: undefined })
    await wrapper.get('.user-toggle').trigger('click')
    await flushPromises()
    expect(wrapper.get('.user-toggle').text()).toBe('ปิดใช้งาน')
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
  it('displays commission tiers without offering an unsupported save action', async () => {
    fixtures['/api/commission/schemes'] = [{ id: 9, name: 'Scheme', active: true }]
    fixtures['/api/commission/schemes/9/tiers'] = [
      { minimum: 0, maximum: null, rate: 0.05, order: 1 },
    ]
    await start()
    await section('Commission Rule')
    expect(wrapper.get('tbody').text()).toContain('5% ของยอดขาย')
    expect(wrapper.find('button.edit').exists()).toBe(false)
    expect(wrapper.find('.settings-add').exists()).toBe(false)
    fixtures['/api/commission/schemes'] = []
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
  it('saves editable course bonus sessions and validity days', async () => {
    await start()
    await section('บริการและคอร์ส')
    await wrapper.findAll('button.edit')[1]!.trigger('click')
    await field('Bonus', '3')
    await field('อายุคอร์ส', '90')
    await save()
    expect(requests).toContainEqual({
      url: '/api/courses/4',
      method: 'PUT',
      body: {
        code: 'C01',
        name: 'Course ten',
        price: 4000,
        sessions: 10,
        bonusSessions: 3,
        validityDays: 90,
        validityValue: 90,
        validityUnit: 'Day',
      },
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
    expect((created?.body as { defaultDurationMinutes?: number } | undefined)?.defaultDurationMinutes).toBeUndefined()
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
  it('persists payment toggles and keeps state on a failed toggle', async () => {
    await start()
    await section('การชำระเงิน')
    expect(wrapper.get('.payment-option').classes()).toContain('selected')
    failedPath = '/api/payment-methods/5'
    await wrapper.get('.payment-option').trigger('click')
    await flushPromises()
    expect(wrapper.get('.payment-option').classes()).toContain('selected')
    failedPath = undefined
    await wrapper.get('.payment-option').trigger('click')
    await flushPromises()
    expect(wrapper.get('.payment-option').classes()).not.toContain('selected')
    expect(requests).toContainEqual({
      url: '/api/payment-methods/5',
      method: 'DELETE',
      body: undefined,
    })
    await wrapper.get('.payment-option').trigger('click')
    await flushPromises()
    expect(wrapper.get('.payment-option').classes()).toContain('selected')
    expect(requests).toContainEqual({
      url: '/api/payment-methods/5/activate',
      method: 'PATCH',
      body: {},
    })
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
        sessions: 10,
        bonusSessions: 2,
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
      { branchCode: '01', code: 'R01', name: 'Updated', type: 'ROOM', status: 'AVAILABLE' },
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
