<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import Request from '../../../api/Request'
import HomepageSettings from './HomepageSettings.vue'
import NativeRecords from './NativeRecords.vue'
import NativePresetPreview from './NativePresetPreview.vue'
import ClientPublication from './ClientPublication.vue'
import NativeSettings from './NativeSettings.vue'
import NativePlugins from './NativePlugins.vue'

type Operation = { id: string; name: string; status: string; included: boolean; managementImplementation?: { state: string } }
type Group = { id: string; title: string; operations: Operation[] }
const catalog = ref<any>(null)
const ui = ref<any>(null)
const history = ref<any[]>([])
const busy = ref(false)
const error = ref('')
const uiError = ref('')
const query = ref('')
const preview = ref<any>(null)
const selectedPreset = ref('native-100')
const selectedCreationPreset = ref('creation-100')
const creationPreview = computed(() => !!edit.value?.preset?.startsWith('creation-'))
const selectedPresetLabel = computed(() => ui.value?.presets?.find((p: any) => p.id === selectedPreset.value)?.label || '所选血槽')
const canApplyPreset = computed(() => ui.value?.nativePresetsCompatible || (selectedPreset.value === 'native-100' && ui.value?.native100Compatible))
type UiEdit = { revision: string; preset?: string; coordinates?: Record<string, number>; layout?: Record<string, number>; switches?: Record<string, boolean>; features?: Record<string, boolean>; values?: Record<string, string>; requestId: string }
const edit = ref<UiEdit | null>(null)
const coordinateValues = ref<Record<string, number>>({})
const switchValues = ref<Record<string, boolean>>({})
const featureValues = ref<Record<string, boolean>>({})
const featureQuery = ref('')
const scalarValues = ref<Record<string, string>>({})
const scalarQuery = ref('')
const scalarGroup = ref('基础与高级参数')
const scalarGroups = ['基础与高级参数', '耳环与他人面板']
const visibleValues = computed(() => (ui.value?.featureValues || []).filter((f: any) => f.group === scalarGroup.value && f.label.includes(scalarQuery.value)))
const layoutValues = ref<Record<string, number>>({})
const layoutGroups = ['创建角色界面', '自定义第一页', '自定义第二页', '自定义第三页']
const visibleFeatures = computed(() => (ui.value?.featureSwitches || []).filter((f: any) => f.label.includes(featureQuery.value)))
const result = ref<any>(null)
const restoreRequests = new Map<string, { revision: string; requestId: string }>()
const root = '/api/v1/admin/dof'
const groups = computed<Group[]>(() => (catalog.value?.scope?.groups || []).filter((g: Group) =>
  (g.title + g.operations.map(o => o.name).join(' ')).includes(query.value)))
function requestId() {
  const bytes = crypto.getRandomValues(new Uint8Array(16))
  bytes[6] = (bytes[6]! & 15) | 64; bytes[8] = (bytes[8]! & 63) | 128
  const hex = Array.from(bytes, b => b.toString(16).padStart(2, '0')).join('')
  return `${hex.slice(0,8)}-${hex.slice(8,12)}-${hex.slice(12,16)}-${hex.slice(16,20)}-${hex.slice(20)}`
}
async function run(action: () => Promise<void>) {
  if (busy.value) return
  busy.value = true; error.value = ''
  try { await action() } catch (e: any) { error.value = e.response?.data?.message || e.message || '连接失败，请核对操作历史' }
  finally { busy.value = false }
}
async function reload() {
  catalog.value = (await Request.get(root)).data
  const [u, h] = await Promise.allSettled([Request.get(`${root}/ui`), Request.get(`${root}/ui/history`)])
  ui.value = u.status === 'fulfilled' ? u.value.data : null
  history.value = h.status === 'fulfilled' ? h.value.data : []
  const messages = [u, h].filter(r => r.status === 'rejected').map(r => r.status === 'rejected' ? r.reason.response?.data?.message || r.reason.message : '')
  uiError.value = messages.join('；')
  coordinateValues.value = Object.fromEntries((ui.value?.coordinates || []).filter((f: any) => f.available).map((f: any) => [f.key, f.value]))
  switchValues.value = Object.fromEntries((ui.value?.switches || []).filter((f: any) => f.available).map((f: any) => [f.key, f.value]))
  featureValues.value = Object.fromEntries((ui.value?.featureSwitches || []).filter((f: any) => f.available).map((f: any) => [f.key, f.value]))
  scalarValues.value = Object.fromEntries((ui.value?.featureValues || []).filter((f: any) => f.available).map((f: any) => [f.key, f.value]))
  layoutValues.value = Object.fromEntries((ui.value?.layoutFields || []).filter((f: any) => f.available).map((f: any) => [f.key, f.value]))
  preview.value = null; edit.value = null
}
function prepare() { return run(async () => {
  preview.value = null
  edit.value = { revision: ui.value.revision, preset: selectedPreset.value, requestId: requestId() }
  preview.value = (await Request.post(`${root}/ui/preview`, edit.value)).data
}) }
function prepareCoordinates() { return run(async () => {
  preview.value = null
  const patch: Record<string, number> = {}
  for (const field of ui.value.coordinates || []) {
    if (field.available && coordinateValues.value[field.key] !== field.value) {
      const value = coordinateValues.value[field.key]
      if (typeof value !== 'number' || !Number.isInteger(value) || value < field.min || value > field.max) throw new Error(`${field.label}必须为范围内的整数`)
      patch[field.key] = value
    }
  }
  if (!Object.keys(patch).length) throw new Error('坐标没有变化')
  edit.value = { revision: ui.value.revision, coordinates: patch, requestId: requestId() }
  preview.value = (await Request.post(`${root}/ui/preview`, edit.value)).data
}) }
function coordinatesChanged() { preview.value = null; edit.value = null }
function presetChanged(id: string) { selectedPreset.value = id; coordinatesChanged() }
function creationPresetChanged(id: string) { selectedCreationPreset.value = id; coordinatesChanged() }
function prepareCreation() { return run(async () => {
  preview.value = null
  edit.value = { revision: ui.value.revision, preset: selectedCreationPreset.value, requestId: requestId() }
  preview.value = (await Request.post(`${root}/ui/preview`, edit.value)).data
}) }
function prepareSwitches() { return run(async () => {
  preview.value = null
  const patch: Record<string, boolean> = {}
  for (const field of ui.value.switches || []) {
    const value = switchValues.value[field.key]
    if (field.available && value !== field.value) {
      if (typeof value !== 'boolean') throw new Error(`${field.label}必须为开启或关闭`)
      patch[field.key] = value
    }
  }
  if (!Object.keys(patch).length) throw new Error('开关没有变化')
  edit.value = { revision: ui.value.revision, switches: patch, requestId: requestId() }
  preview.value = (await Request.post(`${root}/ui/preview`, edit.value)).data
}) }
function save() { return run(async () => {
  if (!edit.value || !preview.value) return
  // Retain the operation ID on failure; a network error must not create another mutation.
  result.value = (await Request.put(`${root}/ui`, edit.value)).data
  await reload()
}) }
function prepareFeatures() { return run(async () => {
  preview.value = null
  const patch: Record<string, boolean> = {}
  for (const field of ui.value.featureSwitches || []) {
    const value = featureValues.value[field.key]
    if (field.editable && value !== field.value) {
      if (typeof value !== 'boolean') throw new Error(`${field.label}必须为开启或关闭`)
      patch[field.key] = value
    }
  }
  if (!Object.keys(patch).length) throw new Error('功能开关没有变化')
  edit.value = { revision: ui.value.revision, features: patch, requestId: requestId() }
  preview.value = (await Request.post(`${root}/ui/preview`, edit.value)).data
}) }
function prepareValues() { return run(async () => {
  preview.value = null
  const patch: Record<string, string> = {}
  for (const field of ui.value.featureValues || []) {
    const value = scalarValues.value[field.key]
    if (field.available && value !== field.value) {
      if (typeof value !== 'string' || value.length > field.maxLength) throw new Error(`${field.label}文本过长或格式不正确`)
      patch[field.key] = value
    }
  }
  if (!Object.keys(patch).length) throw new Error('文本设置没有变化')
  edit.value = { revision: ui.value.revision, values: patch, requestId: requestId() }
  preview.value = (await Request.post(`${root}/ui/preview`, edit.value)).data
}) }
function prepareLayout() { return run(async () => {
  preview.value = null
  const patch: Record<string, number> = {}
  for (const field of ui.value.layoutFields || []) {
    const value = layoutValues.value[field.key]
    if (field.available && value !== field.value) {
      if (typeof value !== 'number' || !Number.isFinite(value) || (!field.decimal && !Number.isInteger(value)) || value < field.min || value > field.max) throw new Error(`${field.label}的数值超出范围`)
      patch[field.key] = value
    }
  }
  if (!Object.keys(patch).length) throw new Error('布局没有变化')
  edit.value = { revision: ui.value.revision, layout: patch, requestId: requestId() }
  preview.value = (await Request.post(`${root}/ui/preview`, edit.value)).data
}) }
function restore(row: any) { return run(async () => {
  const key = `${row.requestId}:${ui.value.revision}`
  if (!restoreRequests.has(key)) restoreRequests.set(key, { revision: ui.value.revision, requestId: requestId() })
  result.value = (await Request.post(`${root}/ui/history/${row.requestId}/restore`, restoreRequests.get(key))).data
  await reload()
}) }
function reconcile(row: any) { return run(async () => {
  result.value = (await Request.post(`${root}/ui/history/${row.requestId}/reconcile`)).data
  await reload()
}) }
onMounted(() => run(reload))
</script>

<template>
  <div class="dof-manager">
    <h2>dof-easy 管理</h2>
    <a-alert v-if="catalog" type="warning">隔离测试 · {{ catalog.instanceId }} · 完整替代开发中，尚未进入统一验收</a-alert>
    <a-alert v-if="error" type="error" class="space">{{ error }}</a-alert>
    <a-button class="space" :loading="busy" @click="run(reload)">刷新实例与配置</a-button>
    <a-alert v-if="uiError" type="warning" class="space">游戏 UI 配置或历史暂不可用：{{ uiError }}。其他管理模块可独立使用。</a-alert>
    <template v-if="catalog">
      <a-tabs class="space">
        <a-tab-pane key="home" title="首页与公告"><HomepageSettings /></a-tab-pane>
        <a-tab-pane key="plugins" title="团本与插件"><NativePlugins /></a-tab-pane>
        <a-tab-pane key="settings" title="注册、泡点与快捷操作"><NativeSettings /></a-tab-pane>
        <a-tab-pane key="records" title="记录查询"><NativeRecords /></a-tab-pane>
        <a-tab-pane v-if="ui" key="ui" title="游戏 UI 管理">
          <a-descriptions title="当前血槽" :column="1" bordered>
            <a-descriptions-item label="当前布局">{{ ui.preset === 'native-100' ? '原生 100 血槽' : '其他原生配置' }}</a-descriptions-item>
            <a-descriptions-item label="血槽调整">{{ ui.switchFormatRecognized ? (ui.bloodLayoutEnabled ? '开启' : '关闭') : '待核对原生格式' }}</a-descriptions-item>
            <a-descriptions-item label="创建角色界面">{{ ui.switchFormatRecognized ? (ui.createRoleLayoutEnabled ? '开启' : '关闭') : '待核对原生格式' }}</a-descriptions-item>
            <a-descriptions-item label="生效方式">{{ ui.activation }}</a-descriptions-item>
          </a-descriptions>
          <NativePresetPreview :presets="ui.presets || []" @select="presetChanged" />
          <a-alert class="space">可预览并保存所选血槽、坐标和开关，保留其他设置。旧版配置不能自动补齐默认值；所有预设的游戏效果仍待统一验证。</a-alert>
          <a-alert v-if="!canApplyPreset" type="warning" class="space">当前配置尚不兼容所选预设，保留原值；需要先完成旧版配置迁移核对。</a-alert>
          <a-form v-if="ui.coordinates?.length" :model="coordinateValues" class="space" layout="vertical">
            <div class="coordinate-grid">
              <a-form-item v-for="field in ui.coordinates" :key="field.key" :label="field.label">
                <a-input-number v-if="field.available" v-model="coordinateValues[field.key]" :min="field.min" :max="field.max" :precision="0" :disabled="busy" @change="coordinatesChanged" />
                <span v-else>原生配置未提供可编辑数值，保留原值</span>
              </a-form-item>
            </div>
            <a-button :disabled="busy" @click="prepareCoordinates">预览坐标变更</a-button>
          </a-form>
          <a-form v-if="ui.switches?.length" :model="switchValues" class="space" layout="vertical">
            <div class="coordinate-grid">
              <a-form-item v-for="field in ui.switches" :key="field.key" :label="field.label">
                <a-switch v-if="field.available" v-model="switchValues[field.key]" :disabled="busy" @change="coordinatesChanged" />
                <span v-else>原生配置未提供可识别开关，保留原值</span>
              </a-form-item>
            </div>
            <a-button :disabled="busy" @click="prepareSwitches">预览开关变更</a-button>
          </a-form>
          <a-space class="space">
            <a-button :disabled="busy || !canApplyPreset" @click="prepare">预览应用{{ selectedPresetLabel }}的变更</a-button>
            <a-button type="primary" :disabled="busy || !preview || !!edit?.features || !!edit?.values || !!edit?.layout || creationPreview" @click="save">保存本次预览</a-button>
          </a-space>
          <p v-if="preview && !edit?.features && !edit?.values && !edit?.layout && !creationPreview">变更预览：{{ preview.changedFields.length ? (edit?.coordinates || edit?.switches ? '仅修改本次选择的界面设置，保留其他字段，并记录修改前快照。' : '将应用所选血槽布局及其附带坐标，记录修改前快照；已核对的旧版100血槽使用完整迁移样本。') : '当前无需改写。' }}保存后仍需重启客户端核对。</p>
          <a-descriptions v-if="preview && edit?.coordinates" title="坐标变更" :column="2" bordered>
            <a-descriptions-item v-for="field in ui.coordinates.filter((f: any) => Object.prototype.hasOwnProperty.call(edit?.coordinates || {}, f.key))" :key="field.key" :label="field.label">{{ field.value }} → {{ edit.coordinates[field.key] }}</a-descriptions-item>
          </a-descriptions>
          <a-descriptions v-if="preview && edit?.switches" title="开关变更" :column="2" bordered>
            <a-descriptions-item v-for="field in ui.switches.filter((f: any) => Object.prototype.hasOwnProperty.call(edit?.switches || {}, f.key))" :key="field.key" :label="field.label">{{ field.value ? '开启' : '关闭' }} → {{ edit.switches[field.key] ? '开启' : '关闭' }}</a-descriptions-item>
          </a-descriptions>
          <p v-if="edit && !edit.features && !edit.values && !edit.layout">本次操作编号：{{ edit.requestId }}。连接中断时请先刷新历史并核对结果。</p>
          <a-alert v-if="result" class="space" :type="result.persisted ? 'success' : 'warning'">
            {{ result.persisted ? '已持久化，游戏效果待确认' : '操作尚未确认持久化，请核对历史' }} · {{ result.requestId }}
          </a-alert>
        </a-tab-pane>
        <a-tab-pane v-if="ui" key="layout" title="创建角色与自定义 UI">
          <p>保存仅调整本次选中的布局数值；血槽和创建角色界面的启用开关请在“游戏 UI 管理”页查看。游戏显示效果仍需重登后核对。</p>
          <NativePresetPreview :presets="ui.creationPresets || []" initial-id="creation-100" title="原工具创建角色预览" selector-label="创建角色预设" @select="creationPresetChanged" />
          <a-button :disabled="busy" @click="prepareCreation">预览应用所选创建角色预设</a-button>
          <a-collapse>
            <a-collapse-item v-for="group in layoutGroups" :key="group" :header="group">
              <a-form :model="layoutValues" layout="vertical">
                <div class="coordinate-grid">
                  <a-form-item v-for="field in (ui.layoutFields || []).filter((f: any) => f.group === group)" :key="field.key" :label="field.label">
                    <a-input-number v-if="field.available" v-model="layoutValues[field.key]" :min="field.min" :max="field.max" :precision="field.decimal ? undefined : 0" :step="field.decimal ? 0.1 : 1" :disabled="busy" @change="coordinatesChanged" />
                    <span v-else>原生布局不完整或数值格式待核对，保留原值</span>
                  </a-form-item>
                </div>
              </a-form>
            </a-collapse-item>
          </a-collapse>
          <a-space class="space">
            <a-button :disabled="busy" @click="prepareLayout">预览布局变更</a-button>
            <a-button type="primary" :disabled="busy || !preview || (!edit?.layout && !creationPreview)" @click="save">保存本次预览</a-button>
          </a-space>
          <a-descriptions v-if="preview && edit?.layout" class="space" title="布局变更" :column="2" bordered>
            <a-descriptions-item v-for="field in ui.layoutFields.filter((f: any) => Object.prototype.hasOwnProperty.call(edit?.layout || {}, f.key))" :key="field.key" :label="field.label">{{ field.value }} → {{ edit.layout[field.key] }}</a-descriptions-item>
          </a-descriptions>
          <a-descriptions v-if="preview && creationPreview" class="space" title="创建角色预设变更" :column="2" bordered>
            <a-descriptions-item v-for="field in preview.after.layoutFields.filter((f: any) => f.key.startsWith('create.'))" :key="field.key" :label="field.label">{{ ui.layoutFields.find((f: any) => f.key === field.key)?.value }} → {{ field.value }}</a-descriptions-item>
          </a-descriptions>
          <p v-if="edit?.layout">本次操作编号：{{ edit.requestId }}。连接中断时请先核对操作历史。</p>
          <a-alert v-if="result" class="space" :type="result.persisted ? 'success' : 'warning'">{{ result.persisted ? '已持久化，游戏效果待确认' : '结果尚未确认，请核对操作历史' }} · {{ result.requestId }}</a-alert>
        </a-tab-pane>
        <a-tab-pane v-if="ui" key="features" title="基础与高级开关">
          <p>读取当前实例原值。保存只修改本次选中的配置，实际效果需由对应游戏组件执行并验证。</p>
          <a-input v-model="featureQuery" placeholder="搜索功能名称" allow-clear />
          <a-form :model="featureValues" class="space" layout="vertical">
            <div class="coordinate-grid">
              <a-form-item v-for="field in visibleFeatures" :key="field.key" :label="field.label">
                <a-switch v-if="field.editable" v-model="featureValues[field.key]" :disabled="busy" @change="coordinatesChanged" />
                <span v-else>{{ field.blockedReason || '当前配置未提供可识别开关，保留原值' }}{{ field.available ? `（当前${field.value ? '开启' : '关闭'}）` : '' }}</span>
              </a-form-item>
            </div>
          </a-form>
          <a-space>
            <a-button :disabled="busy" @click="prepareFeatures">预览功能变更</a-button>
            <a-button type="primary" :disabled="busy || !preview || !edit?.features" @click="save">保存本次预览</a-button>
          </a-space>
          <a-descriptions v-if="preview && edit?.features" class="space" title="功能变更" :column="2" bordered>
            <a-descriptions-item v-for="field in ui.featureSwitches.filter((f: any) => Object.prototype.hasOwnProperty.call(edit?.features || {}, f.key))" :key="field.key" :label="field.label">{{ field.value ? '开启' : '关闭' }} → {{ edit.features[field.key] ? '开启' : '关闭' }}</a-descriptions-item>
          </a-descriptions>
          <p v-if="edit?.features">本次操作编号：{{ edit.requestId }}。连接中断时请先核对操作历史。</p>
          <a-alert v-if="result" class="space" :type="result.persisted ? 'success' : 'warning'">{{ result.persisted ? '已持久化，组件效果待确认' : '结果尚未确认，请核对操作历史' }} · {{ result.requestId }}</a-alert>
        </a-tab-pane>
        <a-tab-pane v-if="ui" key="values" title="基础参数与耳环布局">
          <p>保留原配置的文本、空格和格式。保存只修改本次预览的项目；组件执行效果仍需核对。</p>
          <a-radio-group v-model="scalarGroup" type="button"><a-radio v-for="group in scalarGroups" :key="group" :value="group">{{ group }}</a-radio></a-radio-group>
          <a-input v-model="scalarQuery" class="space" placeholder="搜索设置名称" allow-clear />
          <a-form :model="scalarValues" class="space" layout="vertical">
            <div class="coordinate-grid">
              <a-form-item v-for="field in visibleValues" :key="field.key" :label="field.label">
                <a-input v-if="field.available" v-model="scalarValues[field.key]" :max-length="field.maxLength" :disabled="busy" @input="coordinatesChanged" />
                <span v-else>当前配置缺少此项目或无法读取，保留原值</span>
              </a-form-item>
            </div>
          </a-form>
          <a-space>
            <a-button :disabled="busy" @click="prepareValues">预览参数变更</a-button>
            <a-button type="primary" :disabled="busy || !preview || !edit?.values" @click="save">保存本次预览</a-button>
          </a-space>
          <a-descriptions v-if="preview && edit?.values" class="space" title="参数变更" :column="1" bordered>
            <a-descriptions-item v-for="field in ui.featureValues.filter((f: any) => Object.prototype.hasOwnProperty.call(edit?.values || {}, f.key))" :key="field.key" :label="field.label"><span class="literal-value">{{ JSON.stringify(field.value) }} → {{ JSON.stringify(edit.values[field.key]) }}</span></a-descriptions-item>
          </a-descriptions>
          <p v-if="edit?.values">本次操作编号：{{ edit.requestId }}。连接中断时请先核对操作历史。</p>
          <a-alert v-if="result" class="space" :type="result.persisted ? 'success' : 'warning'">{{ result.persisted ? '已持久化，组件效果待确认' : '结果尚未确认，请核对操作历史' }} · {{ result.requestId }}</a-alert>
        </a-tab-pane>
        <a-tab-pane key="client" title="客户端配置与背景">
          <ClientPublication />
        </a-tab-pane>
        <a-tab-pane key="script" title="Script 加密与更新">
          <a-alert type="warning">尚未完成：需要确认当前客户端实际接受的加密和更新格式，并完成损坏更新的恢复验证。</a-alert>
        </a-tab-pane>
        <a-tab-pane v-if="ui" key="history" title="操作历史与恢复">
          <a-table :data="history" :pagination="{ pageSize: 10 }" row-key="requestId" :scroll="{ x: 1000 }">
            <template #columns>
              <a-table-column title="时间" data-index="createdAt" />
              <a-table-column title="操作者" data-index="actor" />
              <a-table-column title="操作编号" data-index="requestId" />
              <a-table-column title="持久化结果"><template #cell="{record}">{{ ({PERSISTED:'已写入，效果待确认',NO_CHANGE:'无需修改',PREPARED:'结果待核对',NOT_APPLIED:'未写入'} as Record<string,string>)[record.state] || record.state }}</template></a-table-column>
              <a-table-column title="恢复"><template #cell="{record}">
                <a-button v-if="record.state === 'PREPARED'" :disabled="busy" @click="reconcile(record)">核对实际结果</a-button>
                <a-popconfirm v-else-if="record.state === 'PERSISTED'" content="仅恢复这次操作涉及的原生配置；版本不同将拒绝覆盖。确认恢复？" @ok="restore(record)">
                  <a-button :disabled="busy || record.afterRevision !== ui.revision">恢复修改前设置</a-button>
                </a-popconfirm>
              </template></a-table-column>
            </template>
          </a-table>
        </a-tab-pane>
        <a-tab-pane key="scope" title="完整替代清单">
          <p>最低范围 {{ catalog.scope.baselineClasses }} 类、{{ catalog.scope.baselineOperations }} 项；目前已补录到 {{ catalog.scope.discoveredOperations }} 项。全部纳入，原工具菜单仍需补充核对；旧台账的排除结论不适用于本次交付。</p>
          <a-collapse v-if="catalog.executionPlan" class="space">
            <a-collapse-item v-for="pack in catalog.executionPlan.packages" :key="pack.id" :header="`${pack.id} · ${pack.name}`">
              <p>{{ pack.state === 'complete' ? '内部检查已完成' : '内部开发与验证中，尚未完成统一验收' }}</p>
              <ul v-if="pack.progressEvidence?.length"><li v-for="step in pack.progressEvidence" :key="step">已接入：{{ step }}</li></ul>
              <ul><li v-for="step in pack.remaining" :key="step">{{ step }}</li></ul>
            </a-collapse-item>
          </a-collapse>
          <a-descriptions title="已核对的原工具主菜单" :column="3" bordered>
            <a-descriptions-item v-for="menu in catalog.scope.nativeMenus" :key="menu.id" :label="menu.title">子页面待完整对照</a-descriptions-item>
          </a-descriptions>
          <a-input v-model="query" placeholder="搜索原工具功能" allow-clear />
          <a-collapse class="space">
            <a-collapse-item v-for="group in groups" :key="group.id" :header="group.title">
              <div v-for="operation in group.operations" :key="operation.id" class="operation"><span>{{ operation.name }}</span><a-tag v-if="operation.managementImplementation?.state === 'configuration_implemented'" color="blue">配置管理已接入，游戏效果待验</a-tag><a-tag v-else color="orange">待逐项等价验证</a-tag></div>
            </a-collapse-item>
          </a-collapse>
        </a-tab-pane>
      </a-tabs>
    </template>
  </div>
</template>

<style scoped>
.dof-manager { padding: 24px; max-width: 1400px; }
.space { margin-top: 18px; }
.operation { display: flex; justify-content: space-between; gap: 20px; padding: 10px 0; }
.coordinate-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 0 20px; }
.literal-value { white-space: pre-wrap; overflow-wrap: anywhere; }
</style>
