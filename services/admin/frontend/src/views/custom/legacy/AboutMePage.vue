<script setup lang="ts">
import LegacyLayout from './LegacyLayout.vue';
import { useLegacyScripts } from '../../../composables/legacy/useLegacyScripts';

useLegacyScripts(true);
</script>

<template>
  <LegacyLayout>
    <!--Page Title-->
    <section class="page-banner" style="background-image:url(/assets/legacy/images/background/title-bg.jpg);">
      <div class="auto-container">
        <div class="inner-container clearfix">
          <ul class="bread-crumb clearfix">
            <li><a href="#" @click.prevent>Home</a></li>
            <li><a href="#" @click.prevent>Pages</a></li>
            <li>About Me</li>
          </ul>
          <h1>About Me</h1>
        </div>
      </div>
    </section>
    <!--End Page Title-->

    <!-- Player Section -->
    <section class="player-info-section">
      <div class="auto-container">
        <div class="row clearfix">
          <!--Text Column-->
          <div class="text-column col-lg-6 col-md-6 col-sm-12">
            <div class="inner wow fadeInRight">
              <div class="title-box">
                <div class="user-title">wolf smart</div>
                <div class="user-info">20 Games Played . 40 Level Completed</div>
              </div>
              <div class="text">
                There are many variations of passages of lorem ipsum available, but the majority have suffered alteration in some form, by injected humour, or simply free textinh randomised words which don't look even slightly believable. If you are going to use a passage of Lorem Ipsum, you need to be sure there isn't anything embarrassing hidden in the middle of text.
              </div>
              <ul class="social-icons">
                <li><a href="#"><span class="fab fa-twitter"></span></a></li>
                <li><a href="#"><span class="fab fa-facebook-square"></span></a></li>
                <li><a href="#"><span class="fab fa-pinterest-p"></span></a></li>
                <li><a href="#"><span class="fab fa-instagram"></span></a></li>
              </ul>
            </div>
          </div>
          <!--Image Column-->
          <div class="image-column col-lg-6 col-md-6 col-sm-12">
            <div class="inner wow fadeInLeft">
              <figure class="image"><img src="/assets/legacy/images/resource/about-4.jpg" alt="" /></figure>
            </div>
          </div>
        </div>
      </div>
    </section>
    <!-- End About Section -->

    <!--Awards Section-->
    <section class="awards-section">
      <div class="auto-container">
        <div class="sec-title centered">
          <div class="title">Check My All</div>
          <h2>achievements</h2>
        </div>
        <div class="awards-outer">
          <!--Awards Carousel-->
          <ul class="awards-carousel owl-carousel owl-theme">
            <li class="slide-item"><figure class="image-box"><a href="#"><img src="/assets/legacy/images/awards/1.png" alt="" /></a></figure></li>
            <li class="slide-item"><figure class="image-box"><a href="#"><img src="/assets/legacy/images/awards/2.png" alt="" /></a></figure></li>
            <li class="slide-item"><figure class="image-box"><a href="#"><img src="/assets/legacy/images/awards/3.png" alt="" /></a></figure></li>
            <li class="slide-item"><figure class="image-box"><a href="#"><img src="/assets/legacy/images/awards/4.png" alt="" /></a></figure></li>
            <li class="slide-item"><figure class="image-box"><a href="#"><img src="/assets/legacy/images/awards/5.png" alt="" /></a></figure></li>
            <li class="slide-item"><figure class="image-box"><a href="#"><img src="/assets/legacy/images/awards/1.png" alt="" /></a></figure></li>
            <li class="slide-item"><figure class="image-box"><a href="#"><img src="/assets/legacy/images/awards/2.png" alt="" /></a></figure></li>
            <li class="slide-item"><figure class="image-box"><a href="#"><img src="/assets/legacy/images/awards/3.png" alt="" /></a></figure></li>
            <li class="slide-item"><figure class="image-box"><a href="#"><img src="/assets/legacy/images/awards/4.png" alt="" /></a></figure></li>
            <li class="slide-item"><figure class="image-box"><a href="#"><img src="/assets/legacy/images/awards/5.png" alt="" /></a></figure></li>
          </ul>
        </div>
      </div>
    </section>
    <!--End Awards Section-->
  </LegacyLayout>
</template>
