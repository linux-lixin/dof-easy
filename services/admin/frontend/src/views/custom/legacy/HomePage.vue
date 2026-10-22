<script setup lang="ts">
import LegacyLayout from './LegacyLayout.vue';
import { useLegacyScripts } from '../../../composables/legacy/useLegacyScripts';
import { ref } from 'vue';

// 让 legacy 动效在 SPA 下可二次初始化（包含 owl-carousel）
useLegacyScripts(true);

type BigPic = {
  id: number;
  title: string;
  imageUrl: string;
};

const banners = ref<BigPic[]>([
  // 默认占位：接口返回为空 / 请求失败时仍有轮播高度
  { id: 1, title: '别让游戏变成第二份工作。\n这里没有KPI, 只有那个夏天没做完的任务。和随时等你回来的青春云存档。', imageUrl: '/assets/legacy/images/main-slider/d_1.png' },
  { id: 2, title: '放慢速度, 静下心来, 这是我送你的\n一个可以随时暂停、又随时继续的‘那个世界’。', imageUrl: '/assets/legacy/images/main-slider/d_2.png' },
]);

</script>

<template>
  <LegacyLayout>
    <!-- 注意：这里我们将 index.html 的 body 内容逐步迁移进来。
         为了保证“展示完全一致”，首版先保持 DOM/class 结构不变。 -->

    <!-- Banner Section -->
    <section class="banner-section">
      <div class="banner-carousel owl-theme owl-carousel">
        <!-- Slide Item -->
        <div v-for="(b, idx) in banners" :key="b.id || idx" class="slide-item">
          <div class="image-layer" :style="{ backgroundImage: `url(${b.imageUrl})` }"></div>

          <div class="auto-container">
            <div class="content-box">
              <h2 style="font-size: 50px">{{ b.title }}</h2>
              <div class="btn-box">
                <span class="theme-btn btn-style-one"><span class="btn-title">客户端请联系本服管理员</span></span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
    <!--End Banner Section -->

    <!-- Welcome Section -->
    <section class="welcome-section">
      <div class="auto-container">
        <div class="sec-title centered">
          <div class="title">Welcome D&F</div>
          <h2>Dungeon & Fighter</h2>
        </div>

        <div class="row clearfix">
          <div class="default-portfolio-item col-lg-4 col-md-4 col-sm-12 wow fadeInUp" data-wow-delay="0ms" data-wow-duration="1500ms">
            <div class="inner-box hvr-bob">
              <figure class="image-box"><img src="/assets/legacy/images/gallery/d_1.png" alt="" /></figure>
              <div class="overlay-box">
                <div class="overlay-inner">
                  <div class="content">
                    <h3><a href="#" @click.prevent>经典 <br /> 任务</a></h3>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div class="default-portfolio-item col-lg-4 col-md-4 col-sm-12 wow fadeInUp" data-wow-delay="300ms" data-wow-duration="1500ms">
            <div class="inner-box hvr-bob">
              <figure class="image-box"><img src="/assets/legacy/images/gallery/d_2.png" alt="" /></figure>
              <div class="overlay-box">
                <div class="overlay-inner">
                  <div class="content">
                    <h3><a href="#" @click.prevent>经典 <br /> 装备</a></h3>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div class="default-portfolio-item col-lg-4 col-md-4 col-sm-12 wow fadeInUp" data-wow-delay="600ms" data-wow-duration="1500ms">
            <div class="inner-box hvr-bob">
              <figure class="image-box"><img src="/assets/legacy/images/gallery/d_3.png" alt="" /></figure>
              <div class="overlay-box">
                <div class="overlay-inner">
                  <div class="content">
                    <h3><a href="#" @click.prevent>无竞争,更惬意 <br />  随时可玩儿, 慢慢打造</a></h3>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div class="lower-box">
          <div class="text">
            这里是85~90后的回忆！<br />
            我们只是想重温那时候的感觉，不妨放慢速度, 耐心看完每个任务, 耐心打磨每一件装备。 <br />
            您既可以把它当做一个云存档的单机游戏玩耍, 也可以叫上三五个朋友一起组队, 只要不对游戏云端数据刻意破坏, 我们就不会做任何干涉。
          </div>
          <span class="btn-title theme-btn btn-style-one">这一次，我们不赶进度，只赶回忆。给85后的你一个可以随时暂停、又随时继续的‘那个世界’。</span>
        </div>
      </div>
    </section>
    <!-- End Welcome Section -->
  </LegacyLayout>
</template>

<style scoped>
/* 仅限首页轮播：占满视口，超出裁剪 */
.banner-section {
  width: 100vw;
  overflow: hidden;
}

.banner-carousel {
  width: 100vw;
}

.banner-carousel .content-box{
  max-width: 960px!important;
}

.banner-carousel .content-box h2 {
  white-space: pre-line;
}

.banner-carousel .slide-item {
  /* 覆盖 legacy 模板的 padding 撑高方式 */
  padding: 0 !important;
  height: 100vh;
  min-height: 100vh;
  overflow: hidden;
  display: flex;
  align-items: center;
}

.banner-carousel .slide-item .image-layer {
  background-position: center;
  background-size: cover;
}
.image-box img {
  height: 219px;
  width: 370px;
  object-fit: cover;
}
</style>
