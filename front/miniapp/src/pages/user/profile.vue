<template>
  <view class="wg-page" :class="userStore.rootClass">
    <view v-if="loading" class="wg-card">
      <text>正在为您准备，请稍等</text>
    </view>

    <template v-else>
      <!-- 分组 1：称呼与年龄 -->
      <view class="wg-card">
        <view class="wg-label">称呼</view>
        <input class="wg-input" :value="nickname" @input="nickname = $event.detail.value" />
        <view class="wg-label" style="margin-top: 12px">年龄</view>
        <input class="wg-input" type="number" :value="ageText" @input="ageText = $event.detail.value" />
      </view>

      <!-- 分组 2：住址（选填；常用地址在地址簿中集中管理） -->
      <view class="wg-card">
        <view class="wg-label">住址（选填，用于上门/陪同类求助）</view>
        <view v-if="defaultAddress" class="wg-label" style="margin-top: 8px">
          当前默认：{{ defaultAddress.label }} · {{ defaultAddress.areaText }}{{ defaultAddress.detailText ? ' · ' + defaultAddress.detailText : '' }}
        </view>
        <input class="wg-input" placeholder="标签，如：家（填写小区后保存即新增地址）" :value="addressLabel" @input="addressLabel = $event.detail.value" />
        <input class="wg-input" placeholder="小区/区域" :value="addressArea" @input="addressArea = $event.detail.value" />
        <input class="wg-input" placeholder="楼栋门牌（选填）" :value="addressDetail" @input="addressDetail = $event.detail.value" />
        <view class="wg-link-row" @click="goAddress">管理常用地址</view>
      </view>

      <!-- 分组 3：喜欢做的事 -->
      <view class="wg-card">
        <view class="wg-label">喜欢做的事（可多选）</view>
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
      </view>

      <!-- 分组 4：需要留意的小习惯（常驻隐私提示文案） -->
      <view class="wg-card">
        <view class="wg-label">需要留意的小习惯</view>
        <input class="wg-input" placeholder="如：腿脚不便，需要扶一下" :value="careTip" @input="careTip = $event.detail.value" />
        <view class="wg-label" style="margin-top: 8px">
          这段内容会在您求助被接单后，展示给接单的邻居，帮他更好地帮您。请只写需要留意的小习惯（如"腿脚不便，需要扶一下"），不要写密码、身份证号、银行卡号等私人信息。
        </view>
      </view>

      <!-- 分组 5：紧急联系人与手机号 -->
      <view class="wg-card">
        <view class="wg-label">紧急联系人（选填）</view>
        <input class="wg-input" placeholder="姓名" :value="emergencyName" @input="emergencyName = $event.detail.value" />
        <input class="wg-input" placeholder="电话" :value="emergencyPhone" @input="emergencyPhone = $event.detail.value" />
        <view class="wg-label" style="margin-top: 12px">手机号（选填）</view>
        <input class="wg-input" type="number" :value="phone" @input="phone = $event.detail.value" />
      </view>

      <!-- 保存（底部固定，适老加大） -->
      <view class="wg-save-bar">
        <view class="wg-btn" @click="save">保存</view>
      </view>
    </template>
  </view>
</template>

<script>
import { useUserStore } from '@/store/user'
import { getProfile, updateProfile } from '@/api/user'
import { listAddresses, createAddress } from '@/api/address'
import { HOBBY_OPTIONS } from '@/utils/constants'

/**
 * P14 画像编辑（详设 §6.2）：字段同 P02 第 3 步 + 手机号（选填、不校验）。
 * 进入即拉取 U1 回填；保存 → U2；小习惯处常驻同一段隐私提示文案。
 */
export default {
  data() {
    return {
      loading: true,
      nickname: '',
      ageText: '',
      addressLabel: '',
      addressArea: '',
      addressDetail: '',
      defaultAddress: null,
      hobbies: [],
      hobbyOptions: HOBBY_OPTIONS,
      careTip: '',
      emergencyName: '',
      emergencyPhone: '',
      phone: ''
    }
  },
  computed: {
    userStore() {
      return useUserStore()
    }
  },
  onLoad() {
    this.load()
  },
  methods: {
    async load() {
      this.loading = true
      try {
        const profile = await getProfile()
        this.nickname = profile.nickname || ''
        this.ageText = profile.age === null || profile.age === undefined ? '' : String(profile.age)
        this.hobbies = profile.hobbies || []
        this.careTip = profile.careTip || ''
        this.emergencyName = profile.emergencyContactName || ''
        this.emergencyPhone = profile.emergencyContactPhone || ''
        this.phone = profile.phone || ''
      } catch (e) {
        console.error('[微光] 画像加载失败', e)
      } finally {
        this.loading = false
      }
      try {
        const addresses = await listAddresses()
        this.defaultAddress = addresses.find((item) => item.isDefault === 1) || addresses[0] || null
      } catch (e) {
        console.error('[微光] 地址加载失败', e)
      }
    },
    toggleHobby(hobby) {
      const index = this.hobbies.indexOf(hobby)
      if (index >= 0) {
        this.hobbies.splice(index, 1)
      } else {
        this.hobbies.push(hobby)
      }
    },
    goAddress() {
      uni.navigateTo({ url: '/pages/user/address' })
    },
    async save() {
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
      const payload = { hobbies: this.hobbies }
      if (nickname) {
        payload.nickname = nickname
      }
      if (age !== null) {
        payload.age = age
      }
      payload.careTip = this.careTip
      payload.emergencyContactName = this.emergencyName
      payload.emergencyContactPhone = this.emergencyPhone
      payload.phone = this.phone
      try {
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
        uni.showToast({ title: '已保存', icon: 'success' })
        setTimeout(() => {
          uni.navigateBack()
        }, 800)
      } catch (e) {
        console.error('[微光] 画像保存失败', e)
      }
    }
  }
}
</script>

<style lang="scss">
.wg-page {
  padding-bottom: 110px;
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

.wg-link-row {
  margin-top: 12px;
  color: #b06a00;
  font-size: var(--wg-font-base);
}

/* 保存按钮底部固定（详设 §6.2 P14 适老差异：单列分组、保存按钮底部固定加大） */
.wg-save-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 10px var(--wg-gap) calc(10px + env(safe-area-inset-bottom));
  background-color: var(--wg-bg);
  border-top: 1px solid #f0e9dd;
}

.mode-elder .wg-save-bar {
  padding-top: 14px;
  padding-bottom: calc(14px + env(safe-area-inset-bottom));
}
</style>