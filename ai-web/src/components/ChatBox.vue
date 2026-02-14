<template>
  <div>
    <input v-model="query" @keyup.enter="handleSearch" placeholder="Enter your query..." />
    <button @click="handleSearch">Search</button>
    <ul v-if="results.length">
      <li v-for="(result, i) in results" :key="i">{{ result }}</li>
    </ul>
    <p v-else-if="loading">Searching...</p>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { search } from '../api'

const query = ref('')
const results = ref<string[]>([])
const loading = ref(false)

const handleSearch = async () => {
  if (!query.value.trim()) return
  loading.value = true
  try {
    const res = await search(query.value)
    results.value = res.data
  } catch (error) {
    console.error(error)
    results.value = []
  } finally {
    loading.value = false
  }
}
</script>
