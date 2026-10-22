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
            <li><a href="/home">Home</a></li>
            <li>Blog</li>
          </ul>
          <h1>Blog</h1>
        </div>
      </div>
    </section>
    <!--End Page Title-->

    <!-- News Section -->
    <section class="news-section style-two">
      <div class="auto-container">
        <div class="row clearfix">
          <div class="news-block col-lg-4 col-md-6 col-sm-12 wow fadeInUp" data-wow-delay="0ms">
            <div class="inner-box hvr-bob">
              <div class="image">
                <a href="/blog-single"><img src="/assets/legacy/images/resource/news-1.jpg" alt="" /></a>
              </div>
              <div class="lower-content">
                <div class="post-date">20 may 2019</div>
                <h3><a href="/blog-single">important brands have given us their trust</a></h3>
              </div>
            </div>
          </div>

          <div class="news-block col-lg-4 col-md-6 col-sm-12 wow fadeInUp" data-wow-delay="300ms">
            <div class="inner-box hvr-bob">
              <div class="image">
                <a href="/blog-single"><img src="/assets/legacy/images/resource/news-2.jpg" alt="" /></a>
              </div>
              <div class="lower-content">
                <div class="post-date">18 may 2019</div>
                <h3><a href="/blog-single">What happened to esports games this weekend</a></h3>
              </div>
            </div>
          </div>

          <div class="news-block col-lg-4 col-md-6 col-sm-12 wow fadeInUp" data-wow-delay="600ms">
            <div class="inner-box hvr-bob">
              <div class="image">
                <a href="/blog-single"><img src="/assets/legacy/images/resource/news-3.jpg" alt="" /></a>
              </div>
              <div class="lower-content">
                <div class="post-date">16 may 2019</div>
                <h3><a href="/blog-single">Lion king vs wolf smart upcoming matches</a></h3>
              </div>
            </div>
          </div>

          <div class="news-block col-lg-4 col-md-6 col-sm-12 wow fadeInUp" data-wow-delay="0ms">
            <div class="inner-box hvr-bob">
              <div class="image">
                <a href="/blog-single"><img src="/assets/legacy/images/resource/news-4.jpg" alt="" /></a>
              </div>
              <div class="lower-content">
                <div class="post-date">20 may 2019</div>
                <h3><a href="/blog-single">important brands have given us their trust</a></h3>
              </div>
            </div>
          </div>

          <div class="news-block col-lg-4 col-md-6 col-sm-12 wow fadeInUp" data-wow-delay="300ms">
            <div class="inner-box hvr-bob">
              <div class="image">
                <a href="/blog-single"><img src="/assets/legacy/images/resource/news-5.jpg" alt="" /></a>
              </div>
              <div class="lower-content">
                <div class="post-date">18 may 2019</div>
                <h3><a href="/blog-single">What happened to esports games this weekend</a></h3>
              </div>
            </div>
          </div>

          <div class="news-block col-lg-4 col-md-6 col-sm-12 wow fadeInUp" data-wow-delay="600ms">
            <div class="inner-box hvr-bob">
              <div class="image">
                <a href="/blog-single"><img src="/assets/legacy/images/resource/news-6.jpg" alt="" /></a>
              </div>
              <div class="lower-content">
                <div class="post-date">16 may 2019</div>
                <h3><a href="/blog-single">Lion king vs wolf smart upcoming matches</a></h3>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
    <!-- End News Section -->
  </LegacyLayout>
</template>
