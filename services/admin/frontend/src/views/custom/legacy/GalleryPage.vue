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
            <li>Our Gallery</li>
          </ul>
          <h1>Our Gallery</h1>
        </div>
      </div>
    </section>
    <!--End Page Title-->

    <!-- Gallery Section Three -->
    <section class="gallery-section-three">
      <div class="auto-container">
        <div class="row clearfix">
          <div class="gallery-item col-lg-4 col-md-6 col-sm-12 wow fadeInLeft" data-wow-delay="0ms">
            <div class="inner-box">
              <figure class="image-box">
                <img src="/assets/legacy/images/gallery/17.jpg" alt="" />
                <div class="overlay-box">
                  <div class="overlay-inner">
                    <div class="content">
                      <a href="#" class="link" @click.prevent><span class="icon flaticon-unlink"></span></a>
                      <a href="/assets/legacy/images/gallery/17.jpg" data-fancybox="gallery-2" data-caption="" class="link"><span class="icon flaticon-add"></span></a>
                    </div>
                  </div>
                </div>
              </figure>
            </div>
          </div>

          <div class="gallery-item col-lg-4 col-md-6 col-sm-12 wow fadeInLeft" data-wow-delay="300ms">
            <div class="inner-box">
              <figure class="image-box">
                <img src="/assets/legacy/images/gallery/18.jpg" alt="" />
                <div class="overlay-box">
                  <div class="overlay-inner">
                    <div class="content">
                      <a href="#" class="link" @click.prevent><span class="icon flaticon-unlink"></span></a>
                      <a href="/assets/legacy/images/gallery/18.jpg" data-fancybox="gallery-2" data-caption="" class="link"><span class="icon flaticon-add"></span></a>
                    </div>
                  </div>
                </div>
              </figure>
            </div>
          </div>

          <div class="gallery-item col-lg-4 col-md-6 col-sm-12 wow fadeInLeft" data-wow-delay="600ms">
            <div class="inner-box">
              <figure class="image-box">
                <img src="/assets/legacy/images/gallery/19.jpg" alt="" />
                <div class="overlay-box">
                  <div class="overlay-inner">
                    <div class="content">
                      <a href="#" class="link" @click.prevent><span class="icon flaticon-unlink"></span></a>
                      <a href="/assets/legacy/images/gallery/19.jpg" data-fancybox="gallery-2" data-caption="" class="link"><span class="icon flaticon-add"></span></a>
                    </div>
                  </div>
                </div>
              </figure>
            </div>
          </div>

          <div class="gallery-item col-lg-4 col-md-6 col-sm-12 wow fadeInLeft" data-wow-delay="0ms">
            <div class="inner-box">
              <figure class="image-box">
                <img src="/assets/legacy/images/gallery/20.jpg" alt="" />
                <div class="overlay-box">
                  <div class="overlay-inner">
                    <div class="content">
                      <a href="#" class="link" @click.prevent><span class="icon flaticon-unlink"></span></a>
                      <a href="/assets/legacy/images/gallery/20.jpg" data-fancybox="gallery-2" data-caption="" class="link"><span class="icon flaticon-add"></span></a>
                    </div>
                  </div>
                </div>
              </figure>
            </div>
          </div>

          <div class="gallery-item col-lg-4 col-md-6 col-sm-12 wow fadeInLeft" data-wow-delay="300ms">
            <div class="inner-box">
              <figure class="image-box">
                <img src="/assets/legacy/images/gallery/21.jpg" alt="" />
                <div class="overlay-box">
                  <div class="overlay-inner">
                    <div class="content">
                      <a href="#" class="link" @click.prevent><span class="icon flaticon-unlink"></span></a>
                      <a href="/assets/legacy/images/gallery/21.jpg" data-fancybox="gallery-2" data-caption="" class="link"><span class="icon flaticon-add"></span></a>
                    </div>
                  </div>
                </div>
              </figure>
            </div>
          </div>

          <div class="gallery-item col-lg-4 col-md-6 col-sm-12 wow fadeInLeft" data-wow-delay="600ms">
            <div class="inner-box">
              <figure class="image-box">
                <img src="/assets/legacy/images/gallery/22.jpg" alt="" />
                <div class="overlay-box">
                  <div class="overlay-inner">
                    <div class="content">
                      <a href="#" class="link" @click.prevent><span class="icon flaticon-unlink"></span></a>
                      <a href="/assets/legacy/images/gallery/22.jpg" data-fancybox="gallery-2" data-caption="" class="link"><span class="icon flaticon-add"></span></a>
                    </div>
                  </div>
                </div>
              </figure>
            </div>
          </div>

          <!-- 其余 gallery 项（23~28）按原页面继续补齐，确保完全一致 -->
        </div>
      </div>
    </section>
    <!-- End Gallery Section Three -->
  </LegacyLayout>
</template>
