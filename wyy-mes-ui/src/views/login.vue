<template>
  <div class="login">
    <el-form ref="loginForm" :model="loginForm" :rules="loginRules" class="login-form">
      <h3 class="title">王有用MES管理平台</h3>
      <el-form-item prop="username">
        <el-input
          v-model="loginForm.username"
          type="text"
          auto-complete="off"
          placeholder="账号"
        >
          <svg-icon slot="prefix" icon-class="user" class="el-input__icon input-icon" />
        </el-input>
      </el-form-item>
      <el-form-item prop="password">
        <el-input
          v-model="loginForm.password"
          type="password"
          auto-complete="off"
          placeholder="密码"
          @keyup.enter.native="handleLogin"
        >
          <svg-icon slot="prefix" icon-class="password" class="el-input__icon input-icon" />
        </el-input>
      </el-form-item>
      <el-form-item prop="code" v-if="captchaOnOff">
        <el-input
          v-model="loginForm.code"
          auto-complete="off"
          placeholder="验证码"
          style="width: 63%"
          @keyup.enter.native="handleLogin"
        >
          <svg-icon slot="prefix" icon-class="validCode" class="el-input__icon input-icon" />
        </el-input>
        <div class="login-code">
          <img :src="codeUrl" @click="getCode" class="login-code-img"/>
        </div>
      </el-form-item>
      <div class="login-options">
        <el-checkbox v-model="loginForm.rememberMe" class="remember-password">记住密码</el-checkbox>
        <a
          class="app-download"
          :href="appDownloadUrl"
          download="WangYouYong-MES.apk"
          title="下载王有用MES安卓APP"
        >
          APP下载
          <span class="app-download__qr" role="tooltip">
            <img :src="appDownloadQrUrl" alt="扫描下载王有用MES安卓APP" />
            <span>扫码下载安卓版</span>
          </span>
        </a>
      </div>
      <el-form-item style="width:100%;">
        <el-button
          :disabled="loading"
          size="medium"
          type="primary"
          style="width:100%;"
          @click.native.prevent="handleLogin"
        >
          <span>登 录</span>
        </el-button>
        <div style="float: right;" v-if="register">
          <router-link class="link-type" :to="'/register'">立即注册</router-link>
        </div>
      </el-form-item>
    </el-form>
    <!-- MES 登录加载遮罩 -->
    <transition name="fade">
      <div class="mes-loading" v-if="loading">
        <div class="mes-loading__box">
          <div class="mes-loading__gears">
            <svg class="gear gear--big" viewBox="0 0 200 200" xmlns="http://www.w3.org/2000/svg">
              <g fill="#f5a623">
                <rect x="90" y="0" width="20" height="38" rx="4"/>
                <rect x="90" y="0" width="20" height="38" rx="4" transform="rotate(45 100 100)"/>
                <rect x="90" y="0" width="20" height="38" rx="4" transform="rotate(90 100 100)"/>
                <rect x="90" y="0" width="20" height="38" rx="4" transform="rotate(135 100 100)"/>
                <rect x="90" y="0" width="20" height="38" rx="4" transform="rotate(180 100 100)"/>
                <rect x="90" y="0" width="20" height="38" rx="4" transform="rotate(225 100 100)"/>
                <rect x="90" y="0" width="20" height="38" rx="4" transform="rotate(270 100 100)"/>
                <rect x="90" y="0" width="20" height="38" rx="4" transform="rotate(315 100 100)"/>
                <circle cx="100" cy="100" r="56"/>
                <circle cx="100" cy="100" r="22" fill="#1b2531"/>
                <circle cx="100" cy="100" r="9" fill="#f5a623"/>
              </g>
            </svg>
            <svg class="gear gear--small" viewBox="0 0 200 200" xmlns="http://www.w3.org/2000/svg">
              <g fill="#8fa3b8">
                <rect x="90" y="0" width="20" height="38" rx="4"/>
                <rect x="90" y="0" width="20" height="38" rx="4" transform="rotate(45 100 100)"/>
                <rect x="90" y="0" width="20" height="38" rx="4" transform="rotate(90 100 100)"/>
                <rect x="90" y="0" width="20" height="38" rx="4" transform="rotate(135 100 100)"/>
                <rect x="90" y="0" width="20" height="38" rx="4" transform="rotate(180 100 100)"/>
                <rect x="90" y="0" width="20" height="38" rx="4" transform="rotate(225 100 100)"/>
                <rect x="90" y="0" width="20" height="38" rx="4" transform="rotate(270 100 100)"/>
                <rect x="90" y="0" width="20" height="38" rx="4" transform="rotate(315 100 100)"/>
                <circle cx="100" cy="100" r="56"/>
                <circle cx="100" cy="100" r="22" fill="#1b2531"/>
                <circle cx="100" cy="100" r="9" fill="#8fa3b8"/>
              </g>
            </svg>
          </div>
          <div class="mes-loading__text">正在登录系统，请稍候...</div>
          <div class="mes-loading__bar"><div class="mes-loading__bar-inner"></div></div>
        </div>
      </div>
    </transition>
    <!--  底部  -->
    <div class="el-login-footer">
      <span @click="toIPC">陕ICP备2022002135号-1</span>
    </div>
  </div>
</template>

<script>
import { getCodeImg } from "@/api/login";
import Cookies from "js-cookie";
import { encrypt, decrypt } from '@/utils/jsencrypt'

export default {
  name: "Login",
  data() {
    return {
      codeUrl: "",
      loginForm: {
        username: "testuser",
        password: "123456",
        rememberMe: false,
        code: "",
        uuid: ""
      },
      loginRules: {
        username: [
          { required: true, trigger: "blur", message: "请输入您的账号" }
        ],
        password: [
          { required: true, trigger: "blur", message: "请输入您的密码" }
        ],
        code: [{ required: true, trigger: "change", message: "请输入验证码" }]
      },
      loading: false,
      // 验证码开关
      captchaOnOff: true,
      // 注册开关
      register: false,
      redirect: undefined,
      appDownloadUrl: "https://kywgmes.cn/download/wyymes-android.apk"
    };
  },
  computed: {
    appDownloadQrUrl() {
      return "https://api.qrserver.com/v1/create-qr-code/?size=180x180&format=png&data="
        + encodeURIComponent(this.appDownloadUrl);
    }
  },
  watch: {
    $route: {
      handler: function(route) {
        this.redirect = route.query && route.query.redirect;
      },
      immediate: true
    }
  },
  created() {
    this.getCode();
    this.getCookie();
  },
  methods: {
    toIPC(){
      window.open("https://beian.miit.gov.cn/","_blank");
    },
    getCode() {
      getCodeImg().then(res => {
        this.captchaOnOff = res.captchaOnOff === undefined ? true : res.captchaOnOff;
        if (this.captchaOnOff) {
          this.codeUrl = "data:image/gif;base64," + res.img;
          this.loginForm.uuid = res.uuid;
        }
      });
    },
    getCookie() {
      const username = Cookies.get("username");
      const password = Cookies.get("password");
      const rememberMe = Cookies.get('rememberMe')
      this.loginForm = {
        username: username === undefined ? this.loginForm.username : username,
        password: password === undefined ? this.loginForm.password : decrypt(password),
        rememberMe: rememberMe === undefined ? false : Boolean(rememberMe)
      };
    },
    handleLogin() {
      this.$refs.loginForm.validate(valid => {
        if (valid) {
          this.loading = true;
          if (this.loginForm.rememberMe) {
            Cookies.set("username", this.loginForm.username, { expires: 30 });
            Cookies.set("password", encrypt(this.loginForm.password), { expires: 30 });
            Cookies.set('rememberMe', this.loginForm.rememberMe, { expires: 30 });
          } else {
            Cookies.remove("username");
            Cookies.remove("password");
            Cookies.remove('rememberMe');
          }
          this.$store.dispatch("Login", this.loginForm).then(() => {
            this.$router.push({ path: this.redirect || "/" }).catch(()=>{});
          }).catch(() => {
            this.loading = false;
            if (this.captchaOnOff) {
              this.getCode();
            }
          });
        }
      });
    }
  }
};
</script>

<style rel="stylesheet/scss" lang="scss">
.login {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  height: 100%;
  padding-right: 8%;
  background-image: url("../assets/images/login-background.jpg");
  background-size: cover;
  background-position: center;
}
.title {
  margin: 0px auto 30px auto;
  text-align: center;
  color: #707070;
}

.login-form {
  border-radius: 6px;
  background: #ffffff;
  width: 400px;
  padding: 25px 25px 5px 25px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.25);
  .el-input {
    height: 38px;
    input {
      height: 38px;
    }
  }
  .input-icon {
    height: 39px;
    width: 14px;
    margin-left: 2px;
  }
}
.login-tip {
  font-size: 13px;
  text-align: center;
  color: #bfbfbf;
}
.login-code {
  width: 33%;
  height: 38px;
  float: right;
  img {
    cursor: pointer;
    vertical-align: middle;
  }
}
.el-login-footer {
  height: 40px;
  line-height: 40px;
  position: fixed;
  bottom: 0;
  width: 100%;
  text-align: center;
  color: #fff;
  font-family: Arial;
  font-size: 12px;
  letter-spacing: 1px;
}
.login-code-img {
  height: 38px;
}
.login-options {
  display: flex;
  align-items: center;
  height: 22px;
  margin: 0 0 25px;
}
.remember-password {
  margin: 0;
}
.app-download {
  position: relative;
  display: inline-flex;
  align-items: center;
  margin-left: 22px;
  color: #409eff;
  font-size: 14px;
  line-height: 22px;
  text-decoration: none;
  cursor: pointer;
}
.app-download:hover {
  color: #1677d2;
}
.app-download__qr {
  position: absolute;
  right: -10px;
  bottom: 29px;
  z-index: 2;
  display: flex;
  width: 196px;
  padding: 8px 8px 7px;
  flex-direction: column;
  align-items: center;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  background: #fff;
  box-shadow: 0 5px 16px rgba(0, 0, 0, 0.16);
  color: #606266;
  font-size: 12px;
  line-height: 20px;
  opacity: 0;
  pointer-events: none;
  transform: translateY(6px);
  transition: opacity .18s ease, transform .18s ease, visibility .18s ease;
  visibility: hidden;
}
.app-download__qr::after {
  position: absolute;
  right: 24px;
  bottom: -6px;
  width: 10px;
  height: 10px;
  border-right: 1px solid #e4e7ed;
  border-bottom: 1px solid #e4e7ed;
  background: #fff;
  content: "";
  transform: rotate(45deg);
}
.app-download__qr img {
  display: block;
  width: 180px;
  height: 180px;
}
.app-download:hover .app-download__qr,
.app-download:focus .app-download__qr {
  opacity: 1;
  pointer-events: auto;
  transform: translateY(0);
  visibility: visible;
}

/* MES 登录加载遮罩 */
.mes-loading {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 9999;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(13, 20, 32, 0.93);
}
.mes-loading__box {
  text-align: center;
}
.mes-loading__gears {
  position: relative;
  width: 200px;
  height: 180px;
  margin: 0 auto 26px;
}
.gear {
  position: absolute;
}
.gear--big {
  width: 150px;
  height: 150px;
  top: 10px;
  left: 0;
  animation: mes-spin 3.4s linear infinite;
}
.gear--small {
  width: 92px;
  height: 92px;
  right: 2px;
  bottom: 0;
  animation: mes-spin-reverse 2.4s linear infinite;
}
.mes-loading__text {
  color: #cdd6e0;
  font-size: 16px;
  letter-spacing: 2px;
  margin-bottom: 18px;
}
.mes-loading__bar {
  width: 240px;
  height: 6px;
  margin: 0 auto;
  background: rgba(255, 255, 255, 0.12);
  border-radius: 3px;
  overflow: hidden;
}
.mes-loading__bar-inner {
  height: 100%;
  width: 40%;
  border-radius: 3px;
  background: linear-gradient(90deg, #f5a623, #ffcf6b);
  animation: mes-progress 1.4s ease-in-out infinite;
}
@keyframes mes-spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
@keyframes mes-spin-reverse {
  from { transform: rotate(360deg); }
  to { transform: rotate(0deg); }
}
@keyframes mes-progress {
  0% { transform: translateX(-100%); }
  100% { transform: translateX(280%); }
}
</style>
