<template>
  <el-result icon="warning" title="404" sub-title="知识管理页面不存在">
    <template #extra>
      <el-button type="primary" @click="$router.push('/bases')">返回知识库列表</el-button>
    </template>
  </el-result>
</template>
