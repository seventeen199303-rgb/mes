<template>
  <div>
    <svg-icon icon-class="github" @click="goto" />
  </div>
</template>

<script>
export default {
  name: 'WYY-MES-Git',
  data() {
    return {
      url: 'https://gitee.com/fisherit/wyy-mes'
    }
  },
  methods: {
    goto() {
      window.open(this.url)
    }
  }
}
</script>
