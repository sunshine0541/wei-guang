<script>
import { useUserStore } from '@/store/user'
import { useNotifyStore } from '@/store/notify'
import { wsClient } from '@/utils/ws'

export default {
  onLaunch() {
    const userStore = useUserStore()
    const notifyStore = useNotifyStore()
    // 凭证失效：清用户态、解绑通知监听、断开旧连接（request.js 负责清 token 与跳登录）
    uni.$on('wg:auth-expired', () => {
      userStore.$reset()
      userStore.needLogin = true
      notifyStore.unbindWs()
      notifyStore.$reset()
      wsClient.close()
    })
    userStore
      .bootstrap()
      .then(() => {
        if (userStore.needLogin) {
          return
        }
        wsClient.connect()
        notifyStore.refresh()
      })
      .catch((e) => console.error('[微光] 启动初始化失败', e))
  }
}
</script>

<style lang="scss">
@import 'uview-plus/index.scss';
@import './styles/theme.scss';
</style>
