<template>
  <view class="wg-page" :class="userStore.rootClass">
    <view class="wg-card">
      <text class="wg-title">欢迎您</text>
      <text class="wg-label">第 {{ step }} / 4 步 · 每一步都可以跳过，之后在个人中心补全</text>
    </view>

    <!-- 第 1 步：昵称头像 -->
    <view v-if="step === 1" class="wg-card">
      <view class="wg-label">给自己起个称呼</view>
      <button class="wg-btn" open-type="chooseAvatar" @chooseavatar="onChooseAvatar">
        {{ avatarUrl ? '已选择头像，点此更换' : '选择头像' }}
      </button>
      <input
        type="nickname"
        class="wg-input"
        placeholder="点这里填写昵称"
        :value="nickname"
        @input="nickname = $event.detail.value"
      />
      <view class="wg-btn" style="margin-top: 12px" @click="next">下一步</view>
    </view>

    <!-- 第 2 步：模式预置提示 -->
    <view v-if="step === 2" class="wg-card">
      <text>我们为您准备了大字模式，可随时在设置中切换。</text>
      <view class="wg-label" style="margin-top: 8px">当前：{{ userStore.isElder ? '年长模式（大字）' : '普通模式' }}</view>
      <view class="wg-btn" style="margin-top: 12px" @click="useElderMode">立即切换为年长模式</view>
      <view class="wg-btn wg-btn-plain" style="margin-top: 12px" @click="next">保持当前模式</view>
    </view>

    <!-- 第 3 步：画像填写 -->
    <view v-if="step === 3" class="wg-card">
      <view class="wg-label">称呼</view>
      <input class="wg-input" :value="nickname" @input="nickname = $event.detail.value" />
      <view class="wg-label" style="margin-top: 12px">年龄</view>
      <input class="wg-input" type="number" :value="ageText" @input="ageText = $event.detail.value" />
      <view class="wg-label" style="margin-top: 12px">住址（选填，用于上门/陪同类求助）</view>
      <input class="wg-input" placeholder="标签，如：家" :value="addressLabel" @input="addressLabel = $event.detail.value" />
      <input class="wg-input" placeholder="小区/区域" :value="addressArea" @input="addressArea = $event.detail.value" />
      <input class="wg-input" placeholder="楼栋门牌（上门类确认开始后才会给对方看）" :value="addressDetail" @input="addressDetail = $event.detail.value" />
      <view class="wg-label" style="margin-top: 12px">喜欢做的事（可多选）</view>
      <view class="wg-tags">
        <view
          v-for="hobby in hobbyOptions"
          :key="hobby"
          class="wg-tag"
          :class="{ 'wg-tag-active': hobbies.indexOf(hobby) >= 0 }"
          @click="toggleHobby(hobby)"
        >
          {{ hobby }}
        </view>
      </view>
      <view class="wg-label" style="margin-top: 12px">您的反馈，能让帮助者更好地帮助您——这些信息只会在您求助时，展示给接单的邻居。</view>
      <input class="wg-input" placeholder="需要留意的小习惯" :value="careTip" @input="careTip = $event.detail.value" />
      <view class="wg-label">这段内容会在您求助被接单后，展示给接单的邻居，帮他更好地帮您。请只写需要留意的小习惯（如"腿脚不便，需要扶一下"），不要写密码、身份证号、银行卡号等私人信息。</view>
      <view class="wg-label" style="margin-top: 12px">紧急联系人（选填）</view>
      <input class="wg-input" placeholder="姓名" :value="emergencyName" @input="emergencyName = $event.detail.value" />
      <input class="wg-input" placeholder="电话" :value="emergencyPhone" @input="emergencyPhone = $event.detail.value" />
      <view class="wg-btn" style="margin-top: 12px" @click="saveProfile">保存并继续</view>
    </view>

    <!-- 第 4 步：微光邀请 -->
    <view v-if="step === 4" class="wg-card">
      <text>你愿意成为一缕微光，在别人需要时也照亮他一下吗？</text>
      <view class="wg-btn" style="margin-top: 12px" @click="answerInvite('WILLING')">我愿意</view>
      <view class="wg-btn wg-btn-plain" style="margin-top: 12px" @click="answerInvite('LATER')">暂时还不行</view>
    </view>

    <view class="wg-btn wg-btn-plain" @click="finish">跳过，直接进入微光</view>
  </view>
</template>

<script>
import { useUserStore } from '@/store/user'
import { updateProfile } from '@/api/user'
import { createAddress } from '@/api/address'

const HOBBY_OPTIONS = ['聊天', '养花', '下棋', '散步', '做饭', '看书', '手机使用', '手工维修']

export default {
  data() {
    return {
      step: 1,
      nickname: '',
      avatarUrl: '',
      ageText: '',
      addressLabel: '',
      addressArea: '',
      addressDetail: '',
      hobbies: [],
      hobbyOptions: HOBBY_OPTIONS,
      careTip: '',
      emergencyName: '',
      emergencyPhone: ''
    }
  },
  computed: {
    userStore() {
      return useUserStore()
    }
  },
  onLoad() {
    this.nickname = this.userStore.nickname || ''
    this.avatarUrl = this.userStore.avatarUrl || ''
    this.ageText = this.userStore.age === null || this.userStore.age === undefined ? '' : String(this.userStore.age)
  },
  methods: {
    onChooseAvatar(event) {
      this.avatarUrl = event.detail.avatarUrl
    },
    toggleHobby(hobby) {
      const index = this.hobbies.indexOf(hobby)
      if (index >= 0) {
        this.hobbies.splice(index, 1)
      } else {
        this.hobbies.push(hobby)
      }
    },
    async useElderMode() {
      await this.userStore.switchMode('ELDER')
      this.next()
    },
    async saveProfile() {
      const payload = { nickname: this.nickname }
      if (this.ageText !== '') {
        payload.age = Number(this.ageText)
      }
      if (this.hobbies.length > 0) {
        payload.hobbies = this.hobbies
      }
      if (this.careTip) {
        payload.careTip = this.careTip
      }
      if (this.emergencyName) {
        payload.emergencyContactName = this.emergencyName
      }
      if (this.emergencyPhone) {
        payload.emergencyContactPhone = this.emergencyPhone
      }
      await updateProfile(payload)
      if (this.addressArea) {
        await createAddress({
          label: this.addressLabel || '家',
          areaText: this.addressArea,
          detailText: this.addressDetail || null,
          isDefault: 1
        })
      }
      await this.userStore.loadProfile()
      if (Number(this.ageText) >= this.userStore.elderAgeThreshold && !this.userStore.isElder) {
        this.step = 2
        return
      }
      this.step = 4
    },
    async answerInvite(choice) {
      await this.userStore.answerLightInvite(choice)
      this.finish()
    },
    next() {
      this.step = Math.min(this.step + 1, 4)
    },
    finish() {
      uni.redirectTo({ url: '/pages/index/index' })
    }
  }
}
</script>

<style lang="scss">
.wg-input {
  min-height: var(--wg-btn-min-height);
  border: 1px solid #e6ded2;
  border-radius: var(--wg-radius);
  padding: 0 12px;
  margin-top: 8px;
  background-color: #ffffff;
  font-size: var(--wg-font-base);
}

.wg-tags {
  display: flex;
  flex-wrap: wrap;
  margin-top: 8px;
}

.wg-tag {
  padding: 8px 14px;
  margin: 0 8px 8px 0;
  border-radius: var(--wg-radius);
  border: 1px solid #e6ded2;
  background-color: #ffffff;
  font-size: var(--wg-font-base);
}

.wg-tag-active {
  background-color: var(--wg-primary-light);
  border-color: var(--wg-primary);
}
</style>
