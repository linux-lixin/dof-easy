<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { Message } from '@arco-design/web-vue';
import Request from '../../../api/Request';
type Field = { name: string; label: string; type: string; group: string; min: number; max: number; readonly: boolean; default: any };
type Section = { key: string; title: string; primaryKey: string | null; autoId: boolean; writable?: boolean; deleteAllowed?: boolean; notice?: string; fields: Field[] };
type Row = { revision: string; values: Record<string, any> };
const sections = ref<Section[]>([]), active = ref('signin'), rows = ref<Row[]>([]);
const loading = ref(false), saving = ref(false), error = ref(''), notice = ref('');
const edit = ref<Row | null>(null), history = ref<any[]>([]), showHistory = ref(false);
const imported = ref('');
const section = computed(() => sections.value.find(s => s.key === active.value));
const groups = computed(() => [...new Set(section.value?.fields.map(f => f.group) || [])]);
const fields = (group: string) => section.value?.fields.filter(f => f.group === group) || [];
const summaries = computed(() => section.value?.fields.filter(f => ['lv','id','box_id','Dname','item_id','name','d_Name','start_time','end_time','end_date','te_date_end','d_on','d_off','item1','item_code1','item2','item_code2','item3','item_code3'].includes(f.name)) || []);
const endpoint = () => `/api/v1/admin/activities/${active.value}`;
function message(e: any) { return e?.response?.data?.message || e?.message || '操作失败，请重试'; }
async function load() {
  loading.value = true; error.value = ''; rows.value = []; edit.value = null;
  try { const r = await Request.get<any>(endpoint()); rows.value = r.data.rows; notice.value = [r.data.moduleNotice, r.data.notice].filter(Boolean).join(' '); }
  catch (e) { error.value = message(e); }
  finally { loading.value = false; }
}
function begin(row?: Row) {
  imported.value = '';
  if (row) edit.value = JSON.parse(JSON.stringify(row));
  else {
    const values: Record<string, any> = {};
    section.value?.fields.forEach(f => { values[f.name] = f.default; });
    edit.value = { revision: 'new', values };
  }
}
async function save() {
  if (!edit.value || saving.value) return;
  saving.value = true;
  try { await Request.put(endpoint(), edit.value); Message.success('已保存活动配置，玩家进度保留'); await load(); }
  catch (e) { Message.error(message(e)); }
  finally { saving.value = false; }
}
async function remove(row: Row) {
  if (saving.value) return;
  saving.value = true;
  try { await Request.delete(endpoint(), { data: row }); Message.success('配置已删除'); await load(); }
  catch (e) { Message.error(message(e)); }
  finally { saving.value = false; }
}
function download(name: string, value: any) {
  const url = URL.createObjectURL(new Blob([JSON.stringify(value, null, 2)], { type: 'application/json' }));
  const a = document.createElement('a'); a.href = url; a.download = name; a.click(); URL.revokeObjectURL(url);
}
function previewImport() {
  try {
    const value = JSON.parse(imported.value);
    const data = value.values || value;
    const names = section.value!.fields.map(f => f.name).sort();
    if (JSON.stringify(Object.keys(data).sort()) !== JSON.stringify(names)) throw new Error('快照字段不匹配');
    if (edit.value) {
      for (const f of section.value!.fields) {
        if (f.readonly || f.name === section.value!.primaryKey) continue;
        edit.value.values[f.name] = data[f.name];
      }
    }
    Message.info('已填入表单，请核对后保存');
  } catch (e) { Message.error(message(e)); }
}
async function openHistory() {
  try { const r = await Request.get<any[]>(endpoint() + '/history'); history.value = r.data; showHistory.value = true; }
  catch (e) { Message.error(message(e)); }
}
onMounted(async () => {
  loading.value = true;
  try { sections.value = (await Request.get<Section[]>('/api/v1/admin/activities')).data; await load(); }
  catch (e) { error.value = message(e); }
  finally { loading.value = false; }
});
</script>

<template>
  <a-card title="活动管理" style="margin: 24px">
    <a-alert style="margin-bottom: 16px">签到、战令与收集箱沿用配套游戏插件。此处修改活动规则，保留玩家领取和收集进度。活动到期不自动重置玩家记录。</a-alert>
    <a-select v-model="active" @change="load" :disabled="saving" style="width: 360px"><a-option v-for="s in sections" :key="s.key" :value="s.key">{{ s.title }}{{ s.writable === false ? "（只读）" : "" }}</a-option></a-select>
    <a-alert v-if="error" type="error">{{ error }}</a-alert>
    <a-space style="margin: 16px 0" wrap>
      <a-button @click="load" :loading="loading" :disabled="saving">刷新</a-button>
      <a-button type="primary" v-if="section && section.writable !== false && !error && (section.primaryKey || !rows.length)" :disabled="loading || saving" @click="begin()">新增配置</a-button>
      <a-button :disabled="loading || !!error" @click="download(`${active}-config.json`, { section: active, rows })">导出配置</a-button>
      <a-button :disabled="loading || !!error" @click="openHistory">修改记录与快照</a-button>
    </a-space>
    <a-table :data="rows" :loading="loading" :pagination="{ pageSize: 10 }" :scroll="{ x: 1000 }" row-key="revision">
      <template #columns>
        <a-table-column v-for="f in summaries" :key="f.name" :title="f.label">
          <template #cell="{ record }">{{ record.values[f.name] ?? '未设置' }}</template>
        </a-table-column>
        <a-table-column title="操作" :width="180" fixed="right">
          <template #cell="{ record }">
            <a-space><a-button size="small" :disabled="saving" @click="begin(record)">{{ section?.writable === false ? "查看" : "编辑" }}</a-button>
              <a-popconfirm v-if="section?.primaryKey && section.writable !== false && section.deleteAllowed !== false" content="删除这条配置？已有玩家收集记录的槽位不可删除。" @ok="remove(record)">
                <a-button size="small" status="danger" :disabled="saving">删除</a-button>
              </a-popconfirm>
            </a-space>
          </template>
        </a-table-column>
      </template>
    </a-table>
    <p style="color: var(--color-text-3)">{{ notice }}</p>
  </a-card>
  <a-modal :visible="!!edit" :title="section?.title" :width="1000" :footer="false" :mask-closable="false" :closable="!saving" @cancel="edit = null">
    <template v-if="edit && section">
      <a-form :model="edit.values" layout="vertical">
        <a-collapse :default-active-key="groups.slice(0, 1)">
          <a-collapse-item v-for="group in groups" :key="group" :header="group">
            <a-row :gutter="16">
              <a-col v-for="f in fields(group)" :key="f.name" :span="8">
                <a-form-item :label="f.label">
                  <a-input-number v-if="f.type === 'int'" v-model="edit.values[f.name]" :min="f.min" :max="f.max" :precision="0" :disabled="saving || section.writable === false || f.readonly || (f.name === section.primaryKey && (section.autoId || edit.revision !== 'new'))" />
                  <a-input v-else v-model="edit.values[f.name]" :max-length="255" :disabled="saving || section.writable === false || f.readonly" :placeholder="f.type === 'date' ? 'YYYY-MM-DD' : f.type === 'datetime' ? 'YYYY-MM-DD HH:mm:ss' : ''" />
                </a-form-item>
              </a-col>
            </a-row>
          </a-collapse-item>
          <a-collapse-item v-if="section.writable !== false" key="restore" header="从单条配置快照恢复到表单">
            <a-textarea v-model="imported" placeholder="粘贴修改记录中的单条配置 JSON；保留当前编号，核对表单后再保存。" :auto-size="{ minRows: 3, maxRows: 8 }" :disabled="saving" />
            <a-button @click="previewImport" :disabled="saving">填入表单</a-button>
          </a-collapse-item>
        </a-collapse>
      </a-form>
      <a-space style="margin-top: 20px">
        <a-button v-if="section.writable !== false" type="primary" :loading="saving" @click="save">保存配置</a-button>
        <a-button :disabled="saving" @click="edit = null">取消</a-button>
      </a-space>
    </template>
  </a-modal>
  <a-modal v-model:visible="showHistory" title="最近 30 次修改" :width="900" :footer="false">
    <a-alert>每次修改先保存快照。pending 表示结果待核对，请刷新当前配置确认。恢复快照只恢复规则，不回退玩家进度。</a-alert>
    <a-table :data="history" :pagination="{ pageSize: 10 }" row-key="id">
      <template #columns>
        <a-table-column title="时间" data-index="created_at" />
        <a-table-column title="管理员" data-index="actor" />
        <a-table-column title="操作" data-index="operation_name" />
        <a-table-column title="状态" data-index="state" />
        <a-table-column title="快照"><template #cell="{ record }">
          <a-button size="small" @click="download(`${active}-audit-${record.id}.json`, record)">下载</a-button>
          <a-button size="small" :disabled="record.before_json === 'null'" @click="download(`${active}-before-${record.id}.json`, JSON.parse(record.before_json))">修改前配置</a-button>
        </template></a-table-column>
      </template>
    </a-table>
  </a-modal>
</template>
