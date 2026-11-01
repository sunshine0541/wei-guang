<template>
  <view class="wg-page wg-profile-page" :class="userStore.rootClass">
    <view v-if="loading" class="wg-card">
      <text>正在为您准备，请稍等</text>
    </view>

    <template v-else>
      <!-- 分组 1：头像、称呼与年龄 -->
      <view class="wg-card">
        <!-- B：点头像更换头像（open-type=chooseAvatar），换完随“保存”一并落库 -->
        <view class="wg-avatar-row">
          <button class="wg-avatar-btn" open-type="chooseAvatar" @chooseavatar="onChooseAvatar">
            <WgImage v-if="avatarPreview" class="wg-profile-avatar" :src="avatarPreview" :width="56" :height="56" radius="28px" mode="aspectFill" />
            <view v-else class="wg-profile-avatar wg-profile-avatar-empty">换头像</view>
          </button>
          <view class="wg-avatar-tip">
            <text class="wg-avatar-tip-main">换头像</text>
            <text class="wg-label">点头像选一张新照片</text>
          </view>
        </view>
        <view class="wg-label" style="margin-top: 12px">称呼</view>
        <input class="wg-input" maxlength="20" :value="nickname" @input="nickname = $event.detail.value" />
        <view class="wg-label" style="margin-top: 12px">年龄</view>
        <input class="wg-input" type="number" :value="ageText" @input="ageText = $event.detail.value" />
      </view>

      <!-- 分组 2：常用地址（4D-补：地址统一在 P15 维护，这里只展示默认地址） -->
      <view class="wg-card">
        <view class="wg-label">常用地址（用于上门/外出类求助）</view>
        <view v-if="defaultAddress" class="wg-label" style="margin-top: 8px">
          默认：{{ defaultAddress.label }} · {{ defaultAddress.areaText }}{{ defaultAddress.detailText ? ' · ' + defaultAddress.detailText : '' }}
        </view>
        <view v-else class="wg-label" style="margin-top: 8px">
          还没有常用地址，发布上门/外出类求助前先添加一个
        </view>
        <view v-if="defaultAddress" class="wg-label">
          {{ defaultAddressHasGeo ? '已记录地图位置，可用于计算距离' : '未记录地图位置，补上地图位置后可按距离排序' }}
        </view>
        <view class="wg-profile-link" @click="goAddress">
          <u-icon name="map" :size="16" />
          <text class="wg-profile-link-text">管理常用地址</text>
        </view>
      </view>

      <!-- 分组 3：喜欢做的事 -->
      <view class="wg-card">
        <view class="wg-label">喜欢做的事（可多选）</view>
        <view class="wg-tags">
          <view
            v-for="hobby in hobbyOptions"
            :key="hobby"
            class="wg-chip"
            :class="{ 'wg-chip-active': hobbies.indexOf(hobby) >= 0 }"
            @click="toggleHobby(hobby)"
          >
            {{ hobby }}
          </view>
        </view>
      </view>

      <!-- 分组 4：需要留意的小习惯（常驻隐私提示文案） -->
      <view class="wg-card">
        <view class="wg-label">需要留意的小习惯</view>
        <input class="wg-input" maxlength="100" placeholder="如：腿脚不便，需要扶一下" :value="careTip" @input="careTip = $event.detail.value" />
        <view class="wg-label" style="margin-top: 8px">
          这段内容会在您求助被接单后，展示给接单的邻居，帮他更好地帮您。请只写需要留意的小习惯（如"腿脚不便，需要扶一下"），不要写密码、身份证号、银行卡号等私人信息。
        </view>
      </view>

      <!-- 分组 5：紧急联系人与手机号 -->
      <view class="wg-card">
        <view class="wg-label">紧急联系人（选填）</view>
        <input class="wg-input" maxlength="20" placeholder="姓名" :value="emergencyName" @input="emergencyName = $event.detail.value" />
        <input class="wg-input" maxlength="20" placeholder="电话" :value="emergencyPhone" @input="emergencyPhone = $event.detail.value" />
        <!-- B：从手机通讯录选紧急联系人（紧贴紧急联系人字段；不支持则提示手输） -->
        <view class="wg-profile-link" @click="pickContact">
          <u-icon name="phone-fill" :size="16" />
          <text class="wg-profile-link-text">从手机通讯录选</text>
        </view>
        <view class="wg-label" style="margin-top: 12px">手机号（选填）</view>
        <input class="wg-input" type="number" maxlength="11" :value="phone" @input="phone = $event.detail.value" />
      </view>

      <!-- 保存（底部固定，适老加大） -->
      <view class="wg-save-bar">
        <view class="wg-btn" :class="{ 'wg-btn-off': saving }" @click="save">
          {{ saving ? '正在保存…' : '保存' }}
        </view>
      </view>

    </template>
  </view>
</template>

<script>
import { useUserStore } from '@/store/user'
import { getProfile, updateProfile } from '@/api/user'
import { listAddresses } from '@/api/address'
import { uploadImage } from '@/utils/upload'
import { BASE_URL, HOBBY_OPTIONS, MOBILE_PATTERN, normalizePhone } from '@/utils/constants'
import WgImage from '@/components/WgImage.vue'

/**
 * P14 画像编辑（详设 §6.2）：画像字段 + 手机号（选填；填写时校验 11 位手机号）。
 * 4D-补：地址统一在 P15 维护，本页只展示默认地址摘要（不再就地新增）。
 * 进入即拉取 U1 回填；保存 → U2；小习惯处常驻同一段隐私提示文案。
 */
export default {
  components: { WgImage },
  data() {
    return {
      loading: true,
      nickname: '',
      ageText: '',
      defaultAddress: null,
      hobbies: [],
      hobbyOptions: HOBBY_OPTIONS,
      careTip: '',
      emergencyName: '',
      emergencyPhone: '',
      phone: '',
      /** B：头像（可能是服务端相对地址，也可能是刚选的本地临时路径） */
      avatarUrl: '',
      /** B：有未保存修改时拦截返回 */
      dirty: false,
      /** B：回填阶段不触发 dirty */
      hydrating: false,
      /** 保存在途锁：避免连点重复提交 */
      saving: false
    }
  },
  computed: {
    userStore() {
      return useUserStore()
    },
    /** 4D-补：默认地址是否带地图坐标（只做展示提示，不参与编辑） */
    defaultAddressHasGeo() {
      const address = this.defaultAddress
      return !!(address
        && address.latitude !== null && address.latitude !== undefined
        && address.longitude !== null && address.longitude !== undefined)
    },
    /** B：头像预览（本地临时路径直接用；服务端返回的是 /files 相对地址，需拼 BASE_URL） */
    avatarPreview() {
      if (!this.avatarUrl) {
        return ''
      }
      if (/^(wxfile:|https?:\/\/tmp|blob:|https?:)/.test(this.avatarUrl)) {
        return this.avatarUrl
      }
      return BASE_URL + this.avatarUrl
    },
    /** B：所有可编辑字段的快照，任一变化即标记为"未保存" */
    formSnapshot() {
      return [
        this.nickname,
        this.ageText,
        this.hobbies.join(','),
        this.careTip,
        this.emergencyName,
        this.emergencyPhone,
        this.phone,
        this.avatarUrl
      ].join('|')
    }
  },
  watch: {
    formSnapshot() {
      this.markDirty()
    }
  },
  onLoad() {
    this.load()
  },
  onShow() {
    // 从地址簿返回时刷新"当前默认地址"；首次进入由 load() 负责，避免覆盖正在编辑的内容
    if (!this.loading && !this.hydrating) {
      this.refreshDefaultAddress()
    }
  },
  onUnload() {
    this.disableLeaveAlert()
  },
  methods: {
    async load() {
      this.loading = true
      this.hydrating = true
      try {
        const profile = await getProfile()
        this.nickname = profile.nickname || ''
        this.ageText = profile.age === null || profile.age === undefined ? '' : String(profile.age)
        this.hobbies = profile.hobbies || []
        this.careTip = profile.careTip || ''
        this.emergencyName = profile.emergencyContactName || ''
        this.emergencyPhone = profile.emergencyContactPhone || ''
        this.phone = profile.phone || ''
        this.avatarUrl = profile.avatarUrl || ''
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
      // 回填完成后再解锁 dirty 监听
      this.$nextTick(() => {
        this.hydrating = false
      })
    },
    /** 只刷新默认地址摘要，不动其他正在编辑的字段 */
    async refreshDefaultAddress() {
      try {
        const addresses = await listAddresses()
        this.defaultAddress = addresses.find((item) => item.isDefault === 1) || addresses[0] || null
      } catch (e) {
        console.error('[微光] 地址加载失败', e)
      }
    },
    /** B：选择新头像（本地临时路径，随“保存”上传并落库） */
    onChooseAvatar(event) {
      const url = event && event.detail ? event.detail.avatarUrl : ''
      if (url) {
        this.avatarUrl = url
      }
    },
    /** B：标记有未保存修改，并开启返回拦截（微信原生能力，失败静默） */
    markDirty() {
      if (this.hydrating || this.dirty) {
        return
      }
      this.dirty = true
      if (typeof wx !== 'undefined' && typeof wx.enableAlertBeforeUnload === 'function') {
        wx.enableAlertBeforeUnload({ message: '资料还没保存，离开会丢掉这次的修改' })
      }
    },
    disableLeaveAlert() {
      if (typeof wx !== 'undefined' && typeof wx.disableAlertBeforeUnload === 'function') {
        wx.disableAlertBeforeUnload()
      }
    },
    /** B：从手机通讯录选紧急联系人；基础库不支持时退化为手动填写 */
    pickContact() {
      if (typeof wx === 'undefined' || typeof wx.chooseContact !== 'function') {
        uni.showToast({ title: '当前微信不支持读取通讯录，手动填一下吧', icon: 'none' })
        return
      }
      wx.chooseContact({
        success: (res) => {
          if (res && res.displayName) {
            this.emergencyName = res.displayName
          }
          if (res && res.phoneNumber) {
            this.emergencyPhone = normalizePhone(res.phoneNumber)
          }
        },
        fail: (err) => {
          console.error('[微光] 通讯录选择失败', err)
          uni.showToast({ title: '没读到通讯录，手动填一下吧', icon: 'none' })
        }
      })
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
      if (this.saving) {
        return
      }
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
      const emergencyName = this.emergencyName.trim()
      const emergencyPhone = normalizePhone(this.emergencyPhone)
      const ownPhone = normalizePhone(this.phone)
      if (emergencyPhone && !emergencyName) {
        uni.showModal({
          title: '紧急联系人',
          content: '电话已经填好了，再把姓名也填上吧，方便需要时说明找谁。',
          showCancel: false,
          confirmText: '知道了'
        })
        return
      }
      if (emergencyName && !emergencyPhone) {
        uni.showModal({
          title: '紧急联系人',
          content: '姓名已经填好了，再把电话也填上吧，遇到突发情况才能联系到人。',
          showCancel: false,
          confirmText: '知道了'
        })
        return
      }
      if (emergencyPhone && !MOBILE_PATTERN.test(emergencyPhone)) {
        uni.showToast({ title: '请填 11 位手机号', icon: 'none' })
        return
      }
      if (ownPhone && !MOBILE_PATTERN.test(ownPhone)) {
        uni.showToast({ title: '请填 11 位手机号', icon: 'none' })
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
      payload.emergencyContactName = emergencyName
      payload.emergencyContactPhone = emergencyPhone
      payload.phone = ownPhone
      this.saving = true
      // 头像：本地临时路径先上传成 /files 相对地址
      if (this.avatarUrl && this.avatarUrl !== (this.userStore.avatarUrl || '')) {
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
      try {
        await updateProfile(payload)
        await this.userStore.loadProfile()
        // 已保存：解除返回拦截，避免保存后仍弹“未保存”
        this.dirty = false
        this.disableLeaveAlert()
        uni.showToast({ title: '已保存', icon: 'success' })
        setTimeout(() => {
          uni.navigateBack()
        }, 800)
      } catch (e) {
        console.error('[微光] 画像保存失败', e)
        this.saving = false
      }
    }
  }
}
</script>

<style lang="scss">
/* 为底部固定保存栏留出空间 */
.wg-profile-page {
  padding-bottom: 110px;
}

/* B：头像与换头像提示 */
.wg-avatar-row {
  display: flex;
  align-items: center;
}

.wg-avatar-btn {
  padding: 0;
  margin: 0;
  width: 56px;
  height: 56px;
  line-height: normal;
  border-radius: 28px;
  overflow: hidden;
  background-color: transparent;
}

.wg-avatar-btn::after {
  border: none;
}

.wg-profile-avatar {
  width: 56px;
  height: 56px;
  border-radius: 28px;
  background-color: var(--wg-surface);
}

.wg-profile-avatar-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  border: 2px dashed var(--wg-line);
  color: var(--wg-text-weak);
  font-size: var(--wg-font-sm);
  box-sizing: border-box;
}

.wg-avatar-tip {
  display: flex;
  flex-direction: column;
  margin-left: 12px;
}

.wg-avatar-tip-main {
  font-size: var(--wg-font-base);
  font-weight: 800;
  color: var(--wg-primary-deep);
}

.wg-profile-link {
  display: flex;
  align-items: center;
  margin-top: 12px;
  color: var(--wg-primary-deep);
  font-size: var(--wg-font-base);
}

.wg-profile-link-text {
  margin-left: 6px;
}

/* 保存按钮底部固定（详设 §6.2 P14 适老差异：单列分组、保存按钮底部固定加大） */
.wg-save-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 10px var(--wg-gap) calc(10px + env(safe-area-inset-bottom));
  background-color: var(--wg-bg);
  border-top: 1px solid var(--wg-line);
}

.mode-elder .wg-save-bar {
  padding-top: 14px;
  padding-bottom: calc(14px + env(safe-area-inset-bottom));
}
</style>