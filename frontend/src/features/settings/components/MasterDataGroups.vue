<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import FilterDropdown from '@/features/staff/components/FilterDropdown.vue'

type MasterItem = { id: number; category: string; name: string; status: string }
const props = defineProps<{ items: MasterItem[] }>()
const emit = defineEmits<{ edit: [id: number] }>()
const rowsPerPage = 10
const pages = reactive<Record<string, number>>({})
const query = ref('')
const categoryFilter = ref('all')
const expandedCategory = ref('Customer Group')
const categories = [
  { key: 'Customer Group', label: 'กลุ่มลูกค้า' },
  {
    key: 'Referral Channel',
    label: 'ช่องทางแนะนำ',
  },
  {
    key: 'Insurance Type',
    label: 'ประเภทประกัน',
  },
  { key: 'Insurance Company', label: 'บริษัทประกัน' },
]
const categoryOptions = categories.map((category) => ({ value: category.key, label: category.label }))
const groups = computed(() =>
  categories.map((category) => {
    const matchingItems = props.items.filter(
      (item) => item.category === category.key,
    )
    const normalizedQuery = query.value.trim().toLocaleLowerCase('th-TH')
    const items = normalizedQuery
      ? matchingItems.filter((item) =>
          item.name.toLocaleLowerCase('th-TH').includes(normalizedQuery),
        )
      : matchingItems
    const pageCount = Math.max(1, Math.ceil(items.length / rowsPerPage))
    const page = Math.min(pages[category.key] ?? 1, pageCount)
    const start = (page - 1) * rowsPerPage
    return {
      ...category,
      items,
      visibleItems: items.slice(start, start + rowsPerPage),
      page,
      pageCount,
    }
  }),
)
const visibleGroups = computed(() =>
  categoryFilter.value === 'all'
    ? groups.value.filter((group) => group.items.length || !query.value.trim())
    : groups.value.filter((group) => group.key === categoryFilter.value),
)
watch([query, categoryFilter], () => {
  categories.forEach((category) => {
    pages[category.key] = 1
  })
  const first = visibleGroups.value[0]
  if (first) expandedCategory.value = first.key
})
function setPage(category: string, page: number) {
  pages[category] = page
}
</script>

<template>
  <div class="master-groups">
    <div class="master-controls">
      <label class="master-search"
        >ค้นหารายการ<input v-model="query" type="search" placeholder="ค้นหาชื่อข้อมูล"
      /></label>
      <label class="master-filter"
        >ประเภทข้อมูล<FilterDropdown
          v-model="categoryFilter"
          :options="categoryOptions"
          default-value="all"
          default-label="ทั้งหมด"
          label="ประเภทข้อมูล"
          :clear-on-reselect="false"
      /></label>
    </div>
    <section
      v-for="group in visibleGroups"
      :key="group.key"
      class="master-group"
      :aria-label="group.label"
    >
      <button
        type="button"
        class="master-group-heading"
        :class="{ expanded: expandedCategory === group.key }"
        :aria-expanded="expandedCategory === group.key"
        @click="expandedCategory = expandedCategory === group.key ? '' : group.key"
      >
        <div>
          <h3>
            {{ group.label }} <span>{{ group.items.length }} รายการ</span>
          </h3>
        </div>
        <span
          class="master-chevron"
          :class="{ expanded: expandedCategory === group.key }"
          aria-hidden="true"
        ></span>
      </button>
      <div v-if="expandedCategory === group.key" class="master-group-content">
        <ul v-if="group.items.length" class="master-items">
          <li v-for="item in group.visibleItems" :key="item.id">
            <span class="master-item-name">{{ item.name }}</span>
            <span class="settings-status" :class="{ 'status-off': item.status === 'ปิดใช้งาน' }">{{
              item.status
            }}</span>
            <button
              type="button"
              class="edit"
              :aria-label="`แก้ไข ${item.name}`"
              @click="emit('edit', item.id)"
            >
              แก้ไข
            </button>
          </li>
        </ul>
        <div v-if="group.items.length > rowsPerPage" class="master-pagination">
          <span>หน้า {{ group.page }} / {{ group.pageCount }}</span>
          <div>
            <button
              type="button"
              :disabled="group.page === 1"
              @click="setPage(group.key, group.page - 1)"
            >
              ก่อนหน้า
            </button>
            <button
              type="button"
              :disabled="group.page === group.pageCount"
              @click="setPage(group.key, group.page + 1)"
            >
              ถัดไป
            </button>
          </div>
        </div>
        <p v-if="!group.items.length" class="master-empty">
          {{ query ? `ไม่พบรายการ${group.label}ที่ค้นหา` : `ยังไม่มีรายการ${group.label}` }}
        </p>
      </div>
    </section>
    <p v-if="!visibleGroups.length" class="master-empty">ไม่พบข้อมูลตามเงื่อนไขที่เลือก</p>
  </div>
</template>

<style scoped>
.master-groups {
  padding: 0 32px 32px;
}
.master-controls {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(190px, 0.35fr);
  gap: 14px;
  padding: 22px 0 2px;
}
.master-search {
  display: grid;
  gap: 8px;
  color: #40574f;
  font-size: 14px;
  font-weight: 600;
}
.master-filter {
  display: grid;
  gap: 8px;
  color: #40574f;
  font-size: 14px;
  font-weight: 600;
}
.master-filter :deep(.filter-dropdown__button) {
  min-height: 46px;
  border-color: #b5ccc4;
  border-radius: 8px;
  background: #fff;
}
.master-search input {
  box-sizing: border-box;
  width: 100%;
  min-height: 46px;
  padding: 12px 14px;
  border: 1px solid #b5ccc4;
  border-radius: 8px;
  color: #25372e;
  background: #fff;
  font: inherit;
  font-weight: 400;
}
.master-search input::placeholder {
  color: #8a9a93;
  opacity: 0.7;
}
.master-group {
  margin-top: 28px;
}
.master-group + .master-group {
  padding-top: 0;
  border-top: 0;
}
.master-group-heading {
  display: flex;
  width: 100%;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 2px;
  border: 1px solid transparent;
  border-bottom-color: #dfe9e4;
  background: #fff;
  color: #25372e;
  font: inherit;
  text-align: left;
  cursor: pointer;
  margin-bottom: 0;
}
.master-group-heading.expanded {
  border-bottom: 2px solid #178b82;
  background: transparent;
  color: #126f68;
  margin-bottom: 14px;
}
.master-chevron {
  width: 7px;
  height: 7px;
  margin-right: 5px;
  border-right: 1.8px solid #168178;
  border-bottom: 1.8px solid #168178;
  transform: rotate(45deg) translateY(-2px);
  transition: transform 0.18s ease;
}
.master-chevron.expanded {
  transform: rotate(225deg) translate(-2px, -1px);
}
.master-group-content {
  padding-top: 2px;
}
.master-group-heading h3 {
  margin: 0;
  font-size: 20px;
}
.master-group-heading h3 span {
  display: inline-block;
  margin-left: 10px;
  color: #596f65;
  font-size: 13px;
  font-weight: 400;
}
.master-group-heading p,
.master-other > p {
  margin: 5px 0 0;
  color: #596f65;
  font-size: 14px;
}
.master-add {
  flex-shrink: 0;
  min-height: 44px;
  padding: 10px 16px;
  border: 1px solid #1e807c;
  border-radius: 8px;
  background: #fff;
  color: #176965;
  font: inherit;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
}
.master-add:hover {
  background: #eaf5f1;
}
.master-items {
  padding: 0;
  margin: 0;
  list-style: none;
}
.master-items li {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 100px 88px;
  align-items: center;
  gap: 20px;
  padding: 14px 16px;
  background: #f6f9f7;
}
.master-items li + li {
  border-top: 1px solid #e2ebe6;
}
.master-item-name {
  min-width: 0;
  overflow-wrap: anywhere;
  font-weight: 500;
}
.master-item-name small {
  display: block;
  color: #596f65;
  font-weight: 400;
}
.settings-status {
  justify-self: start;
  padding: 5px 12px;
  border-radius: 99px;
  color: #17634b;
  background: #def3e7;
  font-size: 13px;
}
.settings-status.status-off {
  color: #9b322d;
  background: #fce9e7;
}
.edit {
  min-height: 40px;
  border: 1px solid var(--ui-primary);
  border-radius: 8px;
  color: #fff;
  background: var(--ui-primary);
  font: inherit;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
}
.edit:hover {
  filter: brightness(0.95);
}
.master-empty {
  padding: 20px 16px;
  margin: 0;
  border-radius: 8px;
  background: #f6f9f7;
  color: #596f65;
  font-size: 14px;
}
.master-pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 4px 0;
  color: #596f65;
  font-size: 13px;
}
.master-pagination > div {
  display: flex;
  gap: 8px;
}
.master-pagination button {
  min-height: 32px;
  padding: 0 11px;
  border: 1px solid #b5ccc4;
  border-radius: 7px;
  background: #fff;
  color: #176965;
  font: inherit;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}
.master-pagination button:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.master-other {
  margin-top: 28px;
  padding-top: 20px;
  border-top: 1px solid #dfe9e4;
}
.master-other summary {
  min-height: 36px;
  color: #40574f;
  cursor: pointer;
}
.master-other .master-items {
  margin-top: 14px;
}
button:focus-visible,
input:focus-visible,
summary:focus-visible {
  outline: 3px solid #218b85;
  outline-offset: 3px;
}
@media (max-width: 760px) {
  .master-groups {
    padding: 0 20px 24px;
  }
  .master-group-heading {
    align-items: center;
    flex-direction: row;
    gap: 12px;
  }
  .master-controls {
    grid-template-columns: 1fr;
  }
  .master-items li {
    grid-template-columns: minmax(0, 1fr) 80px;
    gap: 10px;
    padding: 14px 12px;
  }
  .master-item-name {
    grid-column: 1 / -1;
  }
  .master-items .edit {
    grid-column: 2;
  }
  .master-add {
    width: 100%;
  }
}
</style>
