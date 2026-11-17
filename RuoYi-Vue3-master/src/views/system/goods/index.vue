<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="68px">
      <el-form-item label="物品名称" prop="goodsName">
        <el-input
          v-model="queryParams.goodsName"
          placeholder="请输入物品名称"
          clearable
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="物品编码/物料编号" prop="goodsNo">
        <el-input
          v-model="queryParams.goodsNo"
          placeholder="请输入物品编码/物料编号"
          clearable
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="物品分类ID" prop="categoryId">
        <el-input
          v-model="queryParams.categoryId"
          placeholder="请输入物品分类ID"
          clearable
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="规格型号" prop="spec">
        <el-input
          v-model="queryParams.spec"
          placeholder="请输入规格型号"
          clearable
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="单位：件/箱/kg" prop="unit">
        <el-input
          v-model="queryParams.unit"
          placeholder="请输入单位：件/箱/kg"
          clearable
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="当前库存数量" prop="stockNum">
        <el-input
          v-model="queryParams.stockNum"
          placeholder="请输入当前库存数量"
          clearable
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="库存预警值" prop="warnStock">
        <el-input
          v-model="queryParams.warnStock"
          placeholder="请输入库存预警值"
          clearable
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="采购成本价" prop="purchasePrice">
        <el-input
          v-model="queryParams.purchasePrice"
          placeholder="请输入采购成本价"
          clearable
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="参考售价" prop="salePrice">
        <el-input
          v-model="queryParams.salePrice"
          placeholder="请输入参考售价"
          clearable
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button
          type="primary"
          plain
          icon="Plus"
          @click="handleAdd"
          v-hasPermi="['system:goods:add']"
        >新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="success"
          plain
          icon="Edit"
          :disabled="single"
          @click="handleUpdate"
          v-hasPermi="['system:goods:edit']"
        >修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="danger"
          plain
          icon="Delete"
          :disabled="multiple"
          @click="handleDelete"
          v-hasPermi="['system:goods:remove']"
        >删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="warning"
          plain
          icon="Download"
          @click="handleExport"
          v-hasPermi="['system:goods:export']"
        >导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="goodsList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="物品主键ID" align="center" prop="goodsId" />
      <el-table-column label="物品名称" align="center" prop="goodsName" />
      <el-table-column label="物品编码/物料编号" align="center" prop="goodsNo" />
      <el-table-column label="物品分类ID" align="center" prop="categoryId" />
      <el-table-column label="规格型号" align="center" prop="spec" />
      <el-table-column label="单位：件/箱/kg" align="center" prop="unit" />
      <el-table-column label="当前库存数量" align="center" prop="stockNum" />
      <el-table-column label="库存预警值" align="center" prop="warnStock" />
      <el-table-column label="采购成本价" align="center" prop="purchasePrice" />
      <el-table-column label="参考售价" align="center" prop="salePrice" />
      <el-table-column label="缩略图地址 MinIO" align="center" prop="thumbUrl" />
      <el-table-column label="原图地址 MinIO" align="center" prop="originUrl" />
      <el-table-column label="备注" align="center" prop="remark" />
      <el-table-column label="状态 0正常 1停用" align="center" prop="status" />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['system:goods:edit']">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['system:goods:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    
    <pagination
      v-show="total>0"
      :total="total"
      v-model:page="queryParams.pageNum"
      v-model:limit="queryParams.pageSize"
      @pagination="getList"
    />

    <!-- 添加或修改仓储物品对话框 -->
    <el-dialog :title="title" v-model="open" width="500px" append-to-body>
      <el-form ref="goodsRef" :model="form" :rules="rules" label-width="100px">
        <el-row>
          <el-col :span="24">
            <el-form-item label="物品名称" prop="goodsName">
              <el-input v-model="form.goodsName" placeholder="请输入物品名称" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="物品编码/物料编号" prop="goodsNo">
              <el-input v-model="form.goodsNo" placeholder="请输入物品编码/物料编号" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="物品分类ID" prop="categoryId">
              <el-input v-model="form.categoryId" placeholder="请输入物品分类ID" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="规格型号" prop="spec">
              <el-input v-model="form.spec" placeholder="请输入规格型号" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="单位：件/箱/kg" prop="unit">
              <el-input v-model="form.unit" placeholder="请输入单位：件/箱/kg" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="当前库存数量" prop="stockNum">
              <el-input v-model="form.stockNum" placeholder="请输入当前库存数量" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="库存预警值" prop="warnStock">
              <el-input v-model="form.warnStock" placeholder="请输入库存预警值" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="采购成本价" prop="purchasePrice">
              <el-input v-model="form.purchasePrice" placeholder="请输入采购成本价" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="参考售价" prop="salePrice">
              <el-input v-model="form.salePrice" placeholder="请输入参考售价" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="缩略图地址 MinIO" prop="thumbUrl">
              <el-input v-model="form.thumbUrl" type="textarea" placeholder="请输入内容" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="原图地址 MinIO" prop="originUrl">
              <el-input v-model="form.originUrl" type="textarea" placeholder="请输入内容" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="form.remark" type="textarea" placeholder="请输入内容" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="删除标志" prop="delFlag">
              <el-input v-model="form.delFlag" placeholder="请输入删除标志" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="Goods">
import { listGoods, getGoods, delGoods, addGoods, updateGoods } from "@/api/system/goods"

const { proxy } = getCurrentInstance()

const goodsList = ref([])
const open = ref(false)
const loading = ref(true)
const showSearch = ref(true)
const ids = ref([])
const single = ref(true)
const multiple = ref(true)
const total = ref(0)
const title = ref("")

const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    goodsName: undefined,
    goodsNo: undefined,
    categoryId: undefined,
    spec: undefined,
    unit: undefined,
    stockNum: undefined,
    warnStock: undefined,
    purchasePrice: undefined,
    salePrice: undefined,
    thumbUrl: undefined,
    originUrl: undefined,
    status: undefined,
  },
  rules: {
    goodsName: [
      { required: true, message: "物品名称不能为空", trigger: "blur" }
    ],
  }
})

const { queryParams, form, rules } = toRefs(data)

/** 查询仓储物品列表 */
function getList() {
  loading.value = true
  listGoods(queryParams.value).then(response => {
    goodsList.value = response.rows
    total.value = response.total
    loading.value = false
  })
}

/** 取消按钮 */
function cancel() {
  open.value = false
  reset()
}

/** 表单重置 */
function reset() {
  form.value = {
    goodsId: null,
    goodsName: null,
    goodsNo: null,
    categoryId: null,
    spec: null,
    unit: null,
    stockNum: null,
    warnStock: null,
    purchasePrice: null,
    salePrice: null,
    thumbUrl: null,
    originUrl: null,
    remark: null,
    status: null,
    createBy: null,
    createTime: null,
    updateBy: null,
    updateTime: null,
    delFlag: null
  }
  proxy.resetForm("goodsRef")
}

/** 搜索按钮操作 */
function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

/** 重置按钮操作 */
function resetQuery() {
  proxy.resetForm("queryRef")
  handleQuery()
}

/** 多选框选中数据 */
function handleSelectionChange(selection) {
  ids.value = selection.map(item => item.goodsId)
  single.value = selection.length != 1
  multiple.value = !selection.length
}

/** 新增按钮操作 */
function handleAdd() {
  reset()
  open.value = true
  title.value = "添加仓储物品"
}

/** 修改按钮操作 */
function handleUpdate(row) {
  reset()
  const _goodsId = row.goodsId || ids.value
  getGoods(_goodsId).then(response => {
    form.value = response.data
    open.value = true
    title.value = "修改仓储物品"
  })
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs["goodsRef"].validate(valid => {
    if (valid) {
      if (form.value.goodsId != null) {
        updateGoods(form.value).then(() => {
          proxy.$modal.msgSuccess("修改成功")
          open.value = false
          getList()
        })
      } else {
        addGoods(form.value).then(() => {
          proxy.$modal.msgSuccess("新增成功")
          open.value = false
          getList()
        })
      }
    }
  })
}

/** 删除按钮操作 */
function handleDelete(row) {
  const _goodsIds = row.goodsId || ids.value
  proxy.$modal.confirm('是否确认删除仓储物品编号为"' + _goodsIds + '"的数据项？').then(function() {
    return delGoods(_goodsIds)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("删除成功")
  }).catch(() => {})
}

/** 导出按钮操作 */
function handleExport() {
  proxy.download('system/goods/export', {
    ...queryParams.value
  }, `goods_${new Date().getTime()}.xlsx`)
}

getList()
</script>
