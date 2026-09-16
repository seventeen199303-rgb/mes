<template>
  <div class="navbar">
    <hamburger id="hamburger-container" :is-active="sidebar.opened" class="hamburger-container" @toggleClick="toggleSideBar" />

    <breadcrumb id="breadcrumb-container" class="breadcrumb-container" v-if="!topNav"/>
    <top-nav id="topmenu-container" class="topmenu-container" v-if="topNav"/>

    <div class="right-menu">
      <template v-if="device!=='mobile'">
        <search id="header-search" class="right-menu-item" />

        <!-- 通知 -->
        <div class="right-menu-item notice-item" @click="goNotice">
          <el-badge :value="unreadCount" :max="99" :hidden="unreadCount === 0" class="notice-badge">
            <i class="el-icon-bell nav-icon"></i>
          </el-badge>
        </div>

      </template>

      <el-dropdown class="avatar-container right-menu-item hover-effect" trigger="click">
        <div class="user-area">
          <img :src="avatar" class="user-avatar" />
          <span class="user-name">{{ nickname }}</span>
        </div>
        <el-dropdown-menu slot="dropdown">
          <router-link to="/user/profile">
            <el-dropdown-item>个人中心</el-dropdown-item>
          </router-link>
          <el-dropdown-item @click.native="setting = true">
            <span>布局设置</span>
          </el-dropdown-item>
          <el-dropdown-item divided @click.native="logout">
            <span>退出登录</span>
          </el-dropdown-item>
        </el-dropdown-menu>
      </el-dropdown>
    </div>
  </div>
</template>

<script>
import { mapGetters } from 'vuex'
import Breadcrumb from '@/components/Breadcrumb'
import TopNav from '@/components/TopNav'
import Hamburger from '@/components/Hamburger'
import Search from '@/components/HeaderSearch'
import { listNotice } from '@/api/system/notice'

export default {
  components: {
    Breadcrumb,
    TopNav,
    Hamburger,
    Search
  },
  computed: {
    ...mapGetters([
      'sidebar',
      'avatar',
      'name',
      'device'
    ]),
    nickname() {
      const nick = this.$store.state.user.nick
      return nick || this.name
    },
    setting: {
      get() {
        return this.$store.state.settings.showSettings
      },
      set(val) {
        this.$store.dispatch('settings/changeSetting', {
          key: 'showSettings',
          value: val
        })
      }
    },
    topNav: {
      get() {
        return this.$store.state.settings.topNav
      }
    }
  },
  data() {
    return {
      unreadCount: 0,
      unreadTimer: null
    }
  },
  mounted() {
    this.getUnreadCount()
    this.unreadTimer = setInterval(() => {
      this.getUnreadCount()
    }, 60 * 1000)
  },
  beforeDestroy() {
    if (this.unreadTimer) {
      clearInterval(this.unreadTimer)
      this.unreadTimer = null
    }
  },
  methods: {
    toggleSideBar() {
      this.$store.dispatch('app/toggleSideBar')
    },
    /** 获取未读通知数量：已发布且正常（status='0'）的通知总数 */
    getUnreadCount() {
      listNotice({ pageNum: 1, pageSize: 1, status: '0' }).then(res => {
        const total = (res && res.total) ? Number(res.total) : 0
        this.unreadCount = total
      }).catch(() => {})
    },
    goNotice() {
      this.$router.push('/system/notice').catch(() => {})
    },
    async logout() {
      this.$confirm('确定注销并退出系统吗？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(() => {
        this.$store.dispatch('LogOut').then(() => {
          location.href = '/index';
        })
      }).catch(() => {});
    }
  }
}
</script>

<style lang="scss" scoped>
.navbar {
  height: 50px;
  overflow: hidden;
  position: relative;
  background: #fff;
  box-shadow: 0 1px 4px rgba(0,21,41,.08);

  .hamburger-container {
    line-height: 46px;
    height: 100%;
    float: left;
    cursor: pointer;
    transition: background .3s;
    -webkit-tap-highlight-color:transparent;

    &:hover {
      background: rgba(0, 0, 0, .025)
    }
  }

  .breadcrumb-container {
    float: left;
  }

  .topmenu-container {
    position: absolute;
    left: 50px;
  }

  .right-menu {
    float: right;
    height: 100%;
    line-height: 50px;

    &:focus {
      outline: none;
    }

    .right-menu-item {
      display: inline-block;
      padding: 0 8px;
      height: 100%;
      font-size: 18px;
      color: #5a5e66;
      vertical-align: text-bottom;

      &.hover-effect {
        cursor: pointer;
        transition: background .3s;

        &:hover {
          background: rgba(0, 0, 0, .025)
        }
      }
    }

    .notice-item {
      cursor: pointer;
      display: inline-flex;
      align-items: center;
      padding: 0 12px;

      &:hover {
        background: rgba(0, 0, 0, .025);
      }

      .nav-icon {
        font-size: 20px;
        color: #5a5e66;
      }

      .notice-badge {
        line-height: 1;
      }
    }

    .avatar-container {
      margin-right: 24px;

      .user-area {
        display: inline-flex;
        align-items: center;
        height: 50px;
        padding: 0 8px;
        cursor: pointer;

        .user-avatar {
          width: 32px;
          height: 32px;
          border-radius: 50%;
          object-fit: cover;
        }

        .user-name {
          margin-left: 8px;
          font-size: 14px;
          color: #303133;
          font-family: "Microsoft YaHei", "微软雅黑", sans-serif;
          max-width: 120px;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
        }
      }
    }
  }
}
</style>