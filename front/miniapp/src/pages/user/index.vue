<template>
  <view class="wg-page wg-page-tab" :class="userStore.rootClass">
    <!-- 头部：灯盏 + 昵称 + 光语状态（§6.2 P13：光语徽标 = 当前状态图标 + 名字，点开一句话解释） -->
    <view class="wg-card wg-me-head">
      <!-- B：有头像时只显示头像，点头像即可更换；光语解释改为点状态文字 -->
      <view v-if="!userStore.avatarUrl" class="wg-lamp" @click="showBadgeExplain">
        <u-icon :name="lightIcon" :size="lightIconSize" />
      </view>
      <button v-else class="wg-me-avatar-btn" open-type="chooseAvatar" @chooseavatar="onChooseAvatar">
        <WgImage class="wg-me-avatar" :src="userStore.avatarUrl" :width="48" :height="48" radius="24px" mode="aspectFill" />
      </button>
      <view class="wg-me-info">
        <text class="wg-me-name">{{ userStore.nickname || '邻居' }}</text>
        <text class="wg-label" @click="showBadgeExplain">{{ light ? light.lightStateName : '未点亮' }} · {{ light ? light.lightStateDesc : '' }}</text>
      </view>
    </view>

    <!-- 数据双色块（微光值 / 积分） -->
    <view class="wg-me-kpi">
      <view class="wg-me-kpi-item" style="background-color: #d9e2d6" @click="goCredit">
        <text class="wg-me-kpi-label" style="color: #4f6b4a">我的微光值</text>
        <text class="wg-me-kpi-value" style="color: #4f6b4a">{{ creditBalance }}</text>
      </view>
      <view class="wg-me-kpi-item" style="background-color: #ede3d0" @click="goPoints">
        <text class="wg-me-kpi-label" style="color: #8a7648">我的荣誉积分</text>
        <text class="wg-me-kpi-value" style="color: #8a7648">{{ pointBalance }}</text>
      </view>
    </view>

    <!-- 分栏 C：我需要的帮助 -->
    <view class="wg-section-title">我需要的帮助</view>
    <view class="wg-rows">
      <view class="wg-row" @click="goMyOrders">
        <text>我的求助</text>
        <text class="wg-row-label">进行中 {{ doingCount }} 件 ›</text>
      </view>
      <view class="wg-row" @click="goProfile">
        <text>我的资料</text>
        <text class="wg-row-label">›</text>
      </view>
      <view class="wg-row" @click="goAddress">
        <text>我的地址</text>
        <text class="wg-row-label">›</text>
      </view>
    </view>

    <!-- 分栏 C：我给出的帮助（助人战绩与求助侧平权） -->
    <view class="wg-section-title">我给出的帮助</view>
    <view class="wg-rows">
      <view class="wg-row" @click="goMineHelping">
        <text>我接取的求助</text>
        <text class="wg-row-label">{{ helpingCount }} 单 ›</text>
      </view>
      <view class="wg-row" @click="goMyEvaluation">
        <text>我收到的评价</text>
        <text class="wg-row-label">{{ evalText }}</text>
      </view>
      <view class="wg-row" @click="goMyThanks">
        <text>我收到的感谢</text>
        <text class="wg-row-label">{{ thanks.length }} 封 ›</text>
      </view>
      <view v-if="light && light.lightState !== 'STEADY'" class="wg-row" @click="openInvite">
        <text>成为微光</text>
        <text class="wg-row-label">›</text>
      </view>
    </view>

    <!-- 感谢墙（§6.2 P13 U10） -->
    <!-- 光已传递徽记（获得时展示，点开一句话解释）；感谢墙已移到独立页面 -->
    <view v-if="light && light.lightPassed === 1" class="wg-card wg-badge-row" @click="showBadgeExplain">
      <u-icon name="checkmark-circle-fill" :size="16" />
      <text class="wg-badge-text">光已传递</text>
    </view>

    <!-- 其他入口 -->
    <view class="wg-rows">
      <view class="wg-row" @click="goNotify">
        <text>通知中心</text>
        <view class="wg-row-label">
          <view v-if="notifyStore.hasUnread" class="wg-dot" />
          <text>›</text>
        </view>
      </view>
      <view class="wg-row" @click="goAppeal">
        <text>反馈与申诉</text>
        <text class="wg-row-label">›</text>
      </view>
      <view class="wg-row" @click="goSettings">
        <text>设置</text>
        <text class="wg-row-label">{{ userStore.isElder ? '年长模式' : '普通模式' }} ›</text>
      </view>
    </view>

    <!-- 微光邀请弹窗（自绘：写死按钮「暂时还不行」5 字超出 showModal 的 4 字限制） -->
    <view v-if="inviteVisible" class="wg-dialog-mask">
      <view class="wg-dialog-box">
        <text class="wg-dialog-text">{{ inviteText }}</text>
        <view class="wg-btn" @click="onInviteWilling">我愿意</view>
        <view class="wg-btn wg-btn-plain" style="margin-top: 12px" @click="onInviteLater">暂时还不行</view>
      </view>
    </view>

    <WgTabBar :active="4" />
  </view>
</template>

<script>
import { useUserStore } from '@/store/user'
import { useNotifyStore } from '@/store/notify'
import { getCreditLedger, getMyThanks, getPointLedger, getNeighborProfile } from '@/api/incentive'
import { listMyOrders } from '@/api/order'
import { getLight, updateProfile } from '@/api/user'
import { uploadImage } from '@/utils/upload'
import WgTabBar from '@/components/WgTabBar.vue'
import WgImage from '@/components/WgImage.vue'

/* 微光邀请弹窗文案（写死：详设 P02 首次邀请 / P13 Q13 再触发） */
const INVITE_TEXT_FIRST = '你愿意成为一缕微光，在别人需要时也照亮他一下吗？'
const INVITE_TEXT_RETRIGGER = '你曾感受过被帮助的温暖，是否也愿意成为一缕微光，照亮他人？'

export default {
  components: { WgTabBar, WgImage },
  data() {
    return {
      creditBalance: '--',
      pointBalance: '--',
      thanks: [],
      light: null,
      doingCount: 0,
      helpingCount: 0,
      evalAvg: '',
      inviteVisible: false,
      inviteText: ''
    }
  },
  computed: {
    userStore() {
      return useUserStore()
    },
    notifyStore() {
      return useNotifyStore()
    },
    lightIcon() {
      if (!this.light) {
        return 'star'
      }
      if (this.light.lightPassed === 1) {
        return 'checkmark-circle-fill'
      }
      if (this.light.lit) {
        return 'star-fill'
      }
      if (this.light.lightState === 'STEADY' || this.light.lightState === 'LIT') {
        return 'star-fill'
      }
      return 'star'
    },
    lightIconSize() {
      return this.userStore.isElder ? 30 : 26
    },
    evalText() {
      return this.evalAvg ? this.evalAvg + ' 分 ›' : '暂无 ›'
    }
  },
  onShow() {
    Promise.all([
      this.load(),
      this.loadStats(),
      this.loadLight(),
      this.notifyStore.refresh()
    ]).catch((e) => console.error('[微光] 个人中心刷新失败', e))
    // A2：隐藏左上角「返回首页⌂」（reLaunch 后页面栈只剩本页，微信会自动加该按钮）
    if (typeof wx !== 'undefined' && typeof wx.hideHomeButton === 'function') {
      wx.hideHomeButton()
    }
  },
  methods: {
    async load() {
      try {
        const [credit, points, wall] = await Promise.all([
          getCreditLedger({ page: 1, pageSize: 1 }),
          getPointLedger({ page: 1, pageSize: 1 }),
          getMyThanks({ page: 1, pageSize: 10 })
        ])
        this.creditBalance = credit.balance || 0
        this.pointBalance = points.balance || 0
        this.thanks = wall.records || []
      } catch (e) {
        console.error('[微光] 个人中心加载失败', e)
      }
    },
    /** 助人战绩：我发布的进行中 / 我接取的求助（复用 O5，失败静默） */
    async loadStats() {
      try {
        const [doing, helping] = await Promise.all([
          listMyOrders({ tab: 'DOING' }),
          listMyOrders({ tab: 'HELPING' })
        ])
        this.doingCount = (doing.records || []).length
        this.helpingCount = (helping.records || []).length
      } catch (e) {
        console.error('[微光] 助人战绩加载失败', e)
      }
    },
    /** B：点头像换头像——上传后立即落库并刷新 */
    async onChooseAvatar(event) {
      const temp = event && event.detail ? event.detail.avatarUrl : ''
      if (!temp) {
        return
      }
      try {
        const url = await uploadImage(temp)
        await updateProfile({ avatarUrl: url })
        await this.userStore.loadProfile()
        uni.showToast({ title: '头像已更新', icon: 'none' })
      } catch (e) {
        console.error('[微光] 头像更新失败', e)
      }
    },
    goCredit() {
      uni.navigateTo({ url: '/pages/user/credit' })
    },
    goMyOrders() {
      uni.navigateTo({ url: '/pages/order/mine' })
    },
    /** 「我接取的求助」→ 我的求助页的第三个 Tab（D16） */
    goMineHelping() {
      uni.navigateTo({ url: '/pages/order/mine?tab=HELPING' })
    },
    /** 「我收到的评价」→ 复用邻居公开页（含本人收到的评价均值与列表，U11/U12） */
    goMyEvaluation() {
      if (!this.userStore.userId) {
        return
      }
      uni.navigateTo({ url: `/pages/neighbor/profile?id=${this.userStore.userId}` })
    },
    /** 「我收到的感谢」→ 独立页面展示 */
    goMyThanks() {
      uni.navigateTo({ url: '/pages/user/thanks' })
    },
    goPoints() {
      uni.navigateTo({ url: '/pages/user/points' })
    },
    goAppeal() {
      uni.navigateTo({ url: '/pages/user/appeal' })
    },
    goProfile() {
      uni.navigateTo({ url: '/pages/user/profile' })
    },
    goAddress() {
      uni.navigateTo({ url: '/pages/user/address' })
    },
    goSettings() {
      uni.navigateTo({ url: '/pages/user/settings' })
    },
    goNotify() {
      uni.navigateTo({ url: '/pages/user/notify' })
    },
    /** U7 光语状态：徽标数据 + 邀请再触发（P13 Q13 规则） */
    async loadLight() {
      try {
        this.light = await getLight()
        if (this.light && this.light.inviteDue) {
          this.showInvite(true)
        }
      } catch (e) {
        console.error('[微光] 光语状态加载失败', e)
      }
      this.loadEvaluation()
    },
    /** 我收到的评价均值（复用 U11 邻居公开页，需本人 id 由 U1 下发） */
    async loadEvaluation() {
      if (!this.userStore.userId) {
        return
      }
      try {
        const profile = await getNeighborProfile(this.userStore.userId)
        this.evalAvg = profile && profile.evaluationAvg != null ? Number(profile.evaluationAvg).toFixed(1) : ''
      } catch (e) {
        console.error('[微光] 我收到的评价加载失败', e)
      }
    },
    showBadgeExplain() {
      if (this.light && this.light.badgeDesc) {
        uni.showModal({ title: '光已传递', content: this.light.badgeDesc, showCancel: false, confirmText: '知道了' })
      } else if (this.light && this.light.lightStateDesc) {
        uni.showModal({
          title: this.light.lightStateName || '光语',
          content: this.light.lightStateDesc,
          showCancel: false,
          confirmText: '知道了'
        })
      }
    },
    /** 微光邀请弹窗（自绘）：auto=true 为结算后再触发文案，否则为首次邀请文案；两种选择均应答 U5 */
    showInvite(auto) {
      this.inviteText = auto ? INVITE_TEXT_RETRIGGER : INVITE_TEXT_FIRST
      this.inviteVisible = true
    },
    openInvite() {
      this.showInvite(false)
    },
    onInviteWilling() {
      this.answerInvite('WILLING')
    },
    onInviteLater() {
      this.answerInvite('LATER')
    },
    async answerInvite(choice) {
      this.inviteVisible = false
      try {
        await this.userStore.answerLightInvite(choice)
        await this.loadLight()
      } catch (e) {
        console.error('[微光] 邀请应答失败', e)
      }
    }
  }
}
</script>

<style lang="scss">
.wg-me-head {
  display: flex;
  align-items: center;
}

.wg-me-info {
  flex: 1;
  margin-left: 12px;
  display: flex;
  flex-direction: column;
}

/* B：个人中心头像（引导页选择后落库展示；有头像时替代灯盏，不再并排两个圆形） */
.wg-me-avatar-btn {
  padding: 0;
  margin: 0;
  width: 48px;
  height: 48px;
  line-height: normal;
  border-radius: 24px;
  overflow: hidden;
  background-color: transparent;
}

.wg-me-avatar-btn::after {
  border: none;
}

.wg-me-avatar {
  width: 48px;
  height: 48px;
  border-radius: 24px;
  background-color: var(--wg-surface);
}

.wg-me-name {
  font-size: var(--wg-font-lg);
  font-weight: 800;
  margin-bottom: 4px;
}

/* 数据双色块 */
.wg-me-kpi {
  display: flex;
  margin-bottom: var(--wg-gap);
}

.wg-me-kpi-item {
  flex: 1;
  border-radius: var(--wg-radius);
  padding: 12px;
  margin-right: 10px;
  display: flex;
  flex-direction: column;
}

.wg-me-kpi-item:last-child {
  margin-right: 0;
}

.wg-me-kpi-label {
  font-size: var(--wg-font-sm);
}

.wg-me-kpi-value {
  font-size: 22px;
  font-weight: 800;
  margin-top: 4px;
}

.mode-elder .wg-me-kpi-value {
  font-size: 26px;
}

.wg-badge-row {
  display: flex;
  align-items: center;
  margin-top: 12px;
  color: var(--wg-primary-deep);
}

.wg-badge-text {
  margin-left: 6px;
  font-size: var(--wg-font-base);
  font-weight: 700;
}
</style>
