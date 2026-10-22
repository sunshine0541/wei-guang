<template>
  <view class="wg-page" :class="userStore.rootClass">
    <view class="wg-card wg-card-tint" style="background-color:#d9e2d6;color:#4f6b4a">
      <text class="wg-title">欢迎您</text>
      <!-- B：4 点进度条替代「第 x / 4 步」文字，弱化阅读负担 -->
      <view class="wg-steps">
        <view
          v-for="n in 4"
          :key="n"
          class="wg-step-dot"
          :class="{ 'wg-step-dot-on': n <= step }"
        />
      </view>
      <text class="wg-label" style="color:#4f6b4a;opacity:.8">每一步都可以跳过，之后在个人中心补全</text>
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

    <!-- 第 2 步：模式预置提示（两种模式可在本页来回切换，选好再点下一步） -->
    <view v-if="step === 2" class="wg-card">
      <text>选择您看着舒服的模式，之后可随时在设置中切换。</text>
      <view
        class="wg-mode-option"
        :class="{ 'wg-mode-option-on': !userStore.isElder }"
        @click="chooseMode('NORMAL')"
      >
        <text class="wg-mode-title">普通模式</text>
        <text class="wg-label">字号与按钮为常规大小</text>
      </view>
      <view
        class="wg-mode-option"
        :class="{ 'wg-mode-option-on': userStore.isElder }"
        @click="chooseMode('ELDER')"
      >
        <text class="wg-mode-title">年长模式（大字）</text>
        <text class="wg-label">字号更大、按钮更好点，动效默认关闭</text>
      </view>
      <view class="wg-btn" style="margin-top: 12px" @click="next">下一步</view>
    </view>

    <!-- 第 3 步：画像填写 -->
    <view v-if="step === 3" class="wg-card">
      <view class="wg-label">称呼</view>
      <input class="wg-input" :value="nickname" @input="nickname = $event.detail.value" />
      <view class="wg-label" style="margin-top: 12px">年龄</view>
      <input class="wg-input" type="number" :value="ageText" @input="ageText = $event.detail.value" />
      <view class="wg-label" style="margin-top: 12px">住址（选填，用于上门/外出类求助）</view>
      <input class="wg-input" placeholder="标签，如：家" :value="addressLabel" @input="addressLabel = $event.detail.value" />
      <input class="wg-input" placeholder="小区/区域" :value="addressArea" @input="addressArea = $event.detail.value" />
      <input class="wg-input" placeholder="楼栋门牌（上门类确认开始后才会给对方看）" :value="addressDetail" @input="addressDetail = $event.detail.value" />
      <view class="wg-label" style="margin-top: 12px">喜欢做的事（可多选）</view>
      <view class="wg-tags">
        <view
          v-for="hobby in hobbyOptions"
          :key="hobby"
          class="wg-tag"
          :class="{ 'wg-chip-active': hobbies.indexOf(hobby) >= 0 }"
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

    <view v-if="step > 1" class="wg-btn wg-btn-plain" style="margin-bottom: 12px" @click="prev">上一步</view>
    <view class="wg-btn wg-btn-plain" @click="finish">跳过，直接进入微光</view>
  </view>
</template>

<script>
import { useUserStore } from '@/store/user'
import { updateProfile } from '@/api/user'
import { createAddress } from '@/api/address'
import { uploadImage } from '@/utils/upload'
import { HOBBY_OPTIONS } from '@/utils/constants'

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
    /** B：模式只切换、不跳步；两种模式都可来回选 */
    async chooseMode(mode) {
      const current = this.userStore.uiMode
      if ((mode === 'ELDER' && current === 'ELDER') || (mode === 'NORMAL' && current !== 'ELDER')) {
        return
      }
      await this.userStore.switchMode(mode)
    },
    async saveProfile() {
      const nickname = this.nickname.trim()
      if (nickname.length > 20) {
        uni.showToast({ title: '称呼最多 20 个字', icon: 'none' })
        return
      }
      let age = null
      if (this.ageText !== '') {
        age = Number(this.ageText)
        if (!Number.isInteger(age) || age < 0 || age > 150) {
          uni.showToast({ title: '年龄请填 0-150 之间的数字', icon: 'none' })
          return
        }
      }
      if (this.careTip.length > 100) {
        uni.showToast({ title: '小习惯最多 100 个字', icon: 'none' })
        return
      }
      if (!this.addressArea.trim() && (this.addressLabel.trim() || this.addressDetail.trim())) {
        uni.showToast({ title: '地址的"小区/区域"填一下才能保存', icon: 'none' })
        return
      }
      const payload = { nickname }
      if (age !== null) {
        payload.age = age
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
      // 头像：chooseAvatar 给的是本地临时路径，需先上传成 /files 相对地址再落库
      if (this.avatarUrl && this.avatarUrl !== this.userStore.avatarUrl) {
        try {
          if (/^(wxfile:|https?:\/\/tmp|blob:)/.test(this.avatarUrl)) {
            payload.avatarUrl = await uploadImage(this.avatarUrl)
          } else {
            payload.avatarUrl = this.avatarUrl
          }
        } catch (e) {
          console.error('[微光] 头像上传失败', e)
        }
      }
      await updateProfile(payload)
      if (this.addressArea.trim()) {
        await createAddress({
          label: this.addressLabel.trim() || '家',
          areaText: this.addressArea.trim(),
          detailText: this.addressDetail.trim() || null,
          isDefault: 1
        })
      }
      await this.userStore.loadProfile()
      if (age !== null && age >= this.userStore.elderAgeThreshold && !this.userStore.isElder) {
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
    prev() {
      this.step = Math.max(this.step - 1, 1)
    },
    finish() {
      uni.redirectTo({ url: '/pages/index/index' })
    }
  }
}
</script>

<style lang="scss" scoped>
/* 可点选的爱好标签：仅补充点击区尺寸，其余样式沿用全局 .wg-tag / .wg-chip-active */
.wg-tag {
  padding: 8px 14px;
  margin: 0 8px 8px 0;
}

/* B：4 点进度条 */
.wg-steps {
  display: flex;
  align-items: center;
  margin-top: 10px;
}

.wg-step-dot {
  width: 26px;
  height: 6px;
  border-radius: 3px;
  margin-right: 6px;
  background-color: rgba(79, 107, 74, 0.25);
}

.wg-step-dot-on {
  background-color: #4f6b4a;
}

/* B：第 2 步的模式二选一卡片 */
.wg-mode-option {
  margin-top: 12px;
  padding: var(--wg-space);
  border: 2px solid var(--wg-line);
  border-radius: var(--wg-radius);
  background-color: var(--wg-surface);
  display: flex;
  flex-direction: column;
}

.wg-mode-option-on {
  border-color: var(--wg-primary);
  background-color: #d9e2d6;
  color: #4f6b4a;
}

.wg-mode-title {
  font-size: var(--wg-font-base);
  font-weight: 800;
}
</style>
