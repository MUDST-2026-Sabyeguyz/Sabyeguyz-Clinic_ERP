<script setup lang="ts">
import { computed } from 'vue'

type MasterItem = { id: number; category: string; name: string; status: string }
const props = defineProps<{ items: MasterItem[] }>()
const emit = defineEmits<{ add: [category: string]; edit: [id: number] }>()
const categories = [
  { key: 'Customer Group', label: 'กลุ่มลูกค้า' },
  {
    key: 'Referral Channel',
    label: 'ช่องทางแนะนำ',
  },
  {
    key: 'Insurance Type',
    label: 'บริษัทประกัน',
  },
]
const groups = computed(() =>
  categories.map((category) => ({
    ...category,
    items: props.items.filter(
      (item) =>
        item.category === category.key ||
        (category.key === 'Insurance Type' && item.category === 'Insurance Company'),
    ),
  })),
)
</script>

<template>
  <div class="master-groups">
    <section
      v-for="group in groups"
      :key="group.key"
      class="master-group"
      :aria-label="group.label"
    >
      <header class="master-group-heading">
        <div>
          <h3>
            {{ group.label }} <span>{{ group.items.length }} รายการ</span>
          </h3>
        </div>
        <button type="button" class="master-add" @click="emit('add', group.key)">
          + เพิ่ม{{ group.label }}
        </button>
      </header>
      <ul v-if="group.items.length" class="master-items">
        <li v-for="item in group.items" :key="item.id">
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
      <p v-else class="master-empty">
        {{ `ยังไม่มีรายการ${group.label} กรุณากด+เพิ่ม` }}
      </p>
    </section>
  </div>
</template>

<style scoped>
.master-groups {
  padding: 0 32px 32px;
}
.master-search {
  display: grid;
  gap: 8px;
  color: #40574f;
  font-size: 14px;
  font-weight: 600;
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
  color: #596f65;
}
.master-group {
  margin-top: 28px;
}
.master-group + .master-group {
  padding-top: 24px;
  border-top: 1px solid #dfe9e4;
}
.master-group-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
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
  border: 1px solid #b5ccc4;
  border-radius: 8px;
  color: #176965;
  background: #fff;
  font: inherit;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
}
.edit:hover {
  border-color: #1e807c;
  background: #eaf5f1;
}
.master-empty {
  padding: 20px 16px;
  margin: 0;
  border-radius: 8px;
  background: #f6f9f7;
  color: #596f65;
  font-size: 14px;
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
    align-items: start;
    flex-direction: column;
    gap: 12px;
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
