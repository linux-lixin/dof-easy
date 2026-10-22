<script setup lang="ts">
import LegacyLayout from './LegacyLayout.vue';
import { useLegacyScripts } from '../../../composables/legacy/useLegacyScripts';

useLegacyScripts(true);
</script>

<template>
  <LegacyLayout>
    <!-- Banner Section -->
    <section class="banner-section style-two">
      <div class="banner-carousel owl-theme owl-carousel">
        <div class="slide-item">
          <div class="image-layer" style="background-image:url(/assets/legacy/images/main-slider/4.jpg)"></div>
          <div class="auto-container">
            <div class="content-box">
              <h2>Gaming and <br /> eSports</h2>
              <div class="text">
                There are many variations of passages of lorem ipsum available, but the majority have suffered alteration
                in some form by injected
              </div>
              <div class="btn-box">
                <a href="#" class="theme-btn btn-style-one" @click.prevent><span class="btn-title">Learn more</span></a>
              </div>
            </div>
          </div>
        </div>

        <div class="slide-item">
          <div class="image-layer" style="background-image:url(/assets/legacy/images/main-slider/5.jpg)"></div>
          <div class="auto-container">
            <div class="content-box">
              <h2>Gaming and <br /> eSports</h2>
              <div class="text">
                There are many variations of passages of lorem ipsum available, but the majority have suffered alteration
                in some form by injected
              </div>
              <div class="btn-box">
                <a href="#" class="theme-btn btn-style-one" @click.prevent><span class="btn-title">Learn more</span></a>
              </div>
            </div>
          </div>
        </div>

        <div class="slide-item">
          <div class="image-layer" style="background-image:url(/assets/legacy/images/main-slider/5.jpg)"></div>
          <div class="auto-container">
            <div class="content-box">
              <h2>Gaming and <br /> eSports</h2>
              <div class="text">
                There are many variations of passages of lorem ipsum available, but the majority have suffered alteration
                in some form by injected
              </div>
              <div class="btn-box">
                <a href="#" class="theme-btn btn-style-one" @click.prevent><span class="btn-title">Learn more</span></a>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
    <!--End Banner Section -->

    <!--Sponsors Section-->
    <section class="sponsors-section dark-bg">
      <div class="auto-container">
        <div class="sponsors-outer">
          <ul class="sponsors-carousel owl-carousel owl-theme">
            <li class="slide-item">
              <figure class="image-box"><a href="#" @click.prevent><img src="/assets/legacy/images/clients/1.png" alt="" /></a></figure>
            </li>
            <li class="slide-item">
              <figure class="image-box"><a href="#" @click.prevent><img src="/assets/legacy/images/clients/2.png" alt="" /></a></figure>
            </li>
            <li class="slide-item">
              <figure class="image-box"><a href="#" @click.prevent><img src="/assets/legacy/images/clients/3.png" alt="" /></a></figure>
            </li>
            <li class="slide-item">
              <figure class="image-box"><a href="#" @click.prevent><img src="/assets/legacy/images/clients/1.png" alt="" /></a></figure>
            </li>
            <li class="slide-item">
              <figure class="image-box"><a href="#" @click.prevent><img src="/assets/legacy/images/clients/2.png" alt="" /></a></figure>
            </li>
            <li class="slide-item">
              <figure class="image-box"><a href="#" @click.prevent><img src="/assets/legacy/images/clients/3.png" alt="" /></a></figure>
            </li>
            <li class="slide-item">
              <figure class="image-box"><a href="#" @click.prevent><img src="/assets/legacy/images/clients/1.png" alt="" /></a></figure>
            </li>
            <li class="slide-item">
              <figure class="image-box"><a href="#" @click.prevent><img src="/assets/legacy/images/clients/2.png" alt="" /></a></figure>
            </li>
            <li class="slide-item">
              <figure class="image-box"><a href="#" @click.prevent><img src="/assets/legacy/images/clients/3.png" alt="" /></a></figure>
            </li>
          </ul>
        </div>
      </div>
    </section>
    <!--End Sponsors Section-->

    <!-- 其余 index-2.html 的模块后续继续按原样补齐 -->
  </LegacyLayout>
</template>
