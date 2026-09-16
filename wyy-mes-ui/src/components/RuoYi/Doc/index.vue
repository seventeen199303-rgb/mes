<template>
  <div>
    <svg-icon icon-class="question" @click="goto" />
  </div>
</template>

<script>
export default {
  name: 'RuoYiDoc',
  data() {
    return {
      url: 'https://github.com/seventeen199303-rgb/wyy-di-mes'
    }
  },
  methods: {
    goto() {
      window.open(this.url)
    }
  }
}
</script>
