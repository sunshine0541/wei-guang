<template>
  <view class="wg-page" :class="userStore.rootClass">
    <view class="wg-card wg-me-head">
      <image v-if="userStore.avatarUrl" class="wg-avatar" :src="fileUrl(userStore.avatarUrl)" mode="aspectFill" />
      <text class="wg-title">{{ userStore.nickname || '邻居' }}</text>
    </view>

    <!-- 光语徽标（详设 §6.2 P13 / §6.4）：当前状态图标+名字，点开一句话解释 -->
    <view class="wg-card">
      <LightBadge
        :name="light ? light.lightStateName : '未点亮'"
        :desc="light ? light.lightStateDesc : ''"
        :light-state="light ? light.lightState : 'UNLIT'"
        :lit="light ? light.lit : false"
      ></LightBadge>
      <view v-if="light && light.lightPassed === 1" class="wg-badge-row" @click="showBadgeExplain">
        <text>💫 光已传递</text>
      </view>
    </view>

    <view class="wg-card" @click="goMyOrders">
      <view class="wg-card-head">
        <text>我的求助</text>
        <text class="wg-label">查看进展</text>
      </view>
    </view>

    <view class="wg-card" @click="goCredit">
      <view class="wg-card-head">
        <text>我的微光值</text>
        <text class="wg-strong">{{ creditBalance }}</text>
      </view>
      <view class="wg-label">查看明细</view>
    </view>

    <view class="wg-card" @click="goPoints">
      <view class="wg-card-head">
        <text>我的积分</text>
        <text class="wg-strong">{{ pointBalance }}</text>
      </view>
      <view class="wg-label">查看明细</view>
    </view>

    <view class="wg-card">
      <text>我收到的感谢</text>
      <view v-if="thanks.length === 0" class="wg-label" style="margin-top: 8px">
        {{ emptyText }}
      </view>
      <view v-for="item in thanks" :key="item.letterId" class="wg-thanks-item">
        <view class="wg-label">{{ item.senderNickname || '邻居' }} · {{ item.title }}</view>
        <text>{{ item.content }}</text>
      </view>
    </view>

    <view class="wg-card" @click="goNotify">
      <view class="wg-card-head">
        <text>通知中心</text>
        <view v-if="notifyStore.hasUnread" class="wg-dot" />
      </view>
    </view>

    <view class="wg-card" @click="goAppeal">
      <view class="wg-card-head">
        <text>反馈与申诉</text>
        <text class="wg-label">查看进度</text>
      </view>
    </view>

    <view class="wg-card" @click="goProfile">
      <view class="wg-card-head">
        <text>我的资料</text>
        <text class="wg-label">编辑</text>
      </view>
    </view>

    <view class="wg-card" @click="goAddress">
      <view class="wg-card-head">
        <text>我的地址</text>
        <text class="wg-label">常用地址</text>
      </view>
    </view>

    <view class="wg-card" @click="goSettings">
      <view class="wg-card-head">
        <text>设置</text>
        <text class="wg-label">{{ userStore.isElder ? '大字模式' : '普通模式' }}</text>
      </view>
    </view>

    <!-- 成为微光入口（详设 §6.2 P13：非"常亮"用户可见） -->
    <view v-if="light && light.lightState !== 'STEADY'" class="wg-card" @click="openInvite">
      <view class="wg-card-head">
        <text>成为微光</text>
        <text class="wg-label">查看</text>
      </view>
    </view>

    <!-- 微光邀请弹窗（自绘：写死按钮"暂时还不行"5 字超出 showModal 的 4 字限制） -->
    <view v-if="inviteVisible" class="wg-invite-mask">
      <view class="wg-invite-box">
        <text class="wg-invite-text">{{ inviteText }}</text>
        <view class="wg-btn wg-invite-btn" @click="onInviteWilling">我愿意</view>
        <view class="wg-btn wg-btn-plain wg-invite-btn" @click="onInviteLater">暂时还不行</view>
      </view>
    </view>
  </view>
</template>

<script>
import { useUserStore } from '@/store/user'
import { useNotifyStore } from '@/store/notify'
import { getCreditLedger, getMyThanks, getPointLedger } from '@/api/incentive'
import { getLight } from '@/api/user'
import { BASE_URL, MESSAGES } from '@/utils/constants'
import LightBadge from '@/components/LightBadge.vue'

/* 微光邀请弹窗文案（写死：详设 P02 首次邀请 / P13 Q13 再触发） */
const INVITE_TEXT_FIRST = '你愿意成为一缕微光，在别人需要时也照亮他一下吗？'
const INVITE_TEXT_RETRIGGER = '你曾感受过被帮助的温暖，是否也愿意成为一缕微光，照亮他人？'

export default {
  components: { LightBadge },
  data() {
    return {
      creditBalance: 0,
      pointBalance: 0,
      thanks: [],
      light: null,
      inviteVisible: false,
      inviteText: '',
      emptyText: MESSAGES.EMPTY
    }
  },
  computed: {
    userStore() {
      return useUserStore()
    },
    notifyStore() {
      return useNotifyStore()
    }
  },
  onShow() {
    this.load()
    this.notifyStore.refresh()
    this.loadLight()
  },
  methods: {
    fileUrl(url) {
      if (!url) {
        return ''
      }
      return url.indexOf('http') === 0 ? url : BASE_URL + url
    },
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
    goCredit() {
      uni.navigateTo({ url: '/pages/user/credit' })
    },
    goMyOrders() {
      uni.navigateTo({ url: '/pages/order/mine' })
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
    },
    showBadgeExplain() {
      if (this.light && this.light.badgeDesc) {
        uni.showModal({ title: '光已传递', content: this.light.badgeDesc, showCancel: false, confirmText: '知道了' })
      }
    },
    /** 微光邀请弹窗（自绘：写死按钮"暂时还不行"5 字超出 showModal 的 4 字限制）；
     * auto=true 为结算后再触发文案，否则为首次邀请文案；两种选择均应答 U5 */
    showInvite(auto) {
      this.inviteText = auto ? INVITE_TEXT_RETRIGGER : INVITE_TEXT_FIRST
      this.inviteVisible = true
    },
    /** 入口点击（模板内联带参调用统一拆为无参方法，避免 wxml 编译兼容性问题） */
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
  flex-direction: column;
  align-items: center;
}

.wg-avatar {
  width: 88px;
  height: 88px;
  border-radius: 50%;
  margin-bottom: 8px;
  background-color: #eee5d8;
}

.wg-strong {
  color: #b06a00;
  font-size: var(--wg-font-lg, 20px);
}

.wg-thanks-item {
  padding: 10px 0;
  border-bottom: 1px solid #f0e9dd;
}

.wg-badge-row {
  margin-top: 10px;
  color: #b06a00;
  font-size: var(--wg-font-base);
}

/* 微光邀请弹窗（自绘） */
.wg-invite-mask {
  position: fixed;
  left: 0;
  top: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 999;
}

.wg-invite-box {
  width: 78%;
  background-color: #ffffff;
  border-radius: var(--wg-radius);
  padding: var(--wg-gap);
}

.wg-invite-text {
  display: block;
  font-size: var(--wg-font-lg);
  line-height: 1.6;
  margin-bottom: var(--wg-gap);
  color: var(--wg-text);
}

.wg-invite-btn {
  margin-top: 12px;
}
</style>