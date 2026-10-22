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
            <li>Our Players</li>
          </ul>
          <h1>Our Players</h1>
        </div>
      </div>
    </section>
    <!--End Page Title-->

    <!-- Players Section -->
    <section class="players-section players-page-section">
      <div class="auto-container">
        <div class="row clearfix">
          <div class="player-block col-lg-4 col-md-6 col-sm-12 wow fadeInLeft" data-wow-delay="0ms">
            <div class="inner-box hvr-bob">
              <div class="image">
                <a href="#" @click.prevent><img src="/assets/legacy/images/resource/player-4.jpg" alt="" /></a>
              </div>
              <div class="lower-content">
                <h3><a href="#" @click.prevent>Lion king</a></h3>
                <div class="level">36 level completed</div>
                <ul class="social-icons">
                  <li><a href="#" @click.prevent><span class="fab fa-twitter"></span></a></li>
                  <li><a href="#" @click.prevent><span class="fab fa-facebook-square"></span></a></li>
                  <li><a href="#" @click.prevent><span class="fab fa-pinterest-p"></span></a></li>
                  <li><a href="#" @click.prevent><span class="fab fa-instagram"></span></a></li>
                </ul>
              </div>
            </div>
          </div>

          <div class="player-block col-lg-4 col-md-6 col-sm-12 wow fadeInLeft" data-wow-delay="300ms">
            <div class="inner-box hvr-bob">
              <div class="image">
                <a href="#" @click.prevent><img src="/assets/legacy/images/resource/player-5.jpg" alt="" /></a>
              </div>
              <div class="lower-content">
                <h3><a href="#" @click.prevent>wolf smart</a></h3>
                <div class="level">40 level completed</div>
                <ul class="social-icons">
                  <li><a href="#" @click.prevent><span class="fab fa-twitter"></span></a></li>
                  <li><a href="#" @click.prevent><span class="fab fa-facebook-square"></span></a></li>
                  <li><a href="#" @click.prevent><span class="fab fa-pinterest-p"></span></a></li>
                  <li><a href="#" @click.prevent><span class="fab fa-instagram"></span></a></li>
                </ul>
              </div>
            </div>
          </div>

          <div class="player-block col-lg-4 col-md-6 col-sm-12 wow fadeInLeft" data-wow-delay="600ms">
            <div class="inner-box hvr-bob">
              <div class="image">
                <a href="#" @click.prevent><img src="/assets/legacy/images/resource/player-6.jpg" alt="" /></a>
              </div>
              <div class="lower-content">
                <h3><a href="#" @click.prevent>johnnybravo</a></h3>
                <div class="level">90 level completed</div>
                <ul class="social-icons">
                  <li><a href="#" @click.prevent><span class="fab fa-twitter"></span></a></li>
                  <li><a href="#" @click.prevent><span class="fab fa-facebook-square"></span></a></li>
                  <li><a href="#" @click.prevent><span class="fab fa-pinterest-p"></span></a></li>
                  <li><a href="#" @click.prevent><span class="fab fa-instagram"></span></a></li>
                </ul>
              </div>
            </div>
          </div>

          <div class="player-block col-lg-4 col-md-6 col-sm-12 wow fadeInLeft" data-wow-delay="0ms">
            <div class="inner-box hvr-bob">
              <div class="image">
                <a href="#" @click.prevent><img src="/assets/legacy/images/resource/player-7.jpg" alt="" /></a>
              </div>
              <div class="lower-content">
                <h3><a href="#" @click.prevent>Gamer_xboy</a></h3>
                <div class="level">75 level completed</div>
                <ul class="social-icons">
                  <li><a href="#" @click.prevent><span class="fab fa-twitter"></span></a></li>
                  <li><a href="#" @click.prevent><span class="fab fa-facebook-square"></span></a></li>
                  <li><a href="#" @click.prevent><span class="fab fa-pinterest-p"></span></a></li>
                  <li><a href="#" @click.prevent><span class="fab fa-instagram"></span></a></li>
                </ul>
              </div>
            </div>
          </div>

          <div class="player-block col-lg-4 col-md-6 col-sm-12 wow fadeInLeft" data-wow-delay="300ms">
            <div class="inner-box hvr-bob">
              <div class="image">
                <a href="#" @click.prevent><img src="/assets/legacy/images/resource/player-8.jpg" alt="" /></a>
              </div>
              <div class="lower-content">
                <h3><a href="#" @click.prevent>warrior-782</a></h3>
                <div class="level">40 level completed</div>
                <ul class="social-icons">
                  <li><a href="#" @click.prevent><span class="fab fa-twitter"></span></a></li>
                  <li><a href="#" @click.prevent><span class="fab fa-facebook-square"></span></a></li>
                  <li><a href="#" @click.prevent><span class="fab fa-pinterest-p"></span></a></li>
                  <li><a href="#" @click.prevent><span class="fab fa-instagram"></span></a></li>
                </ul>
              </div>
            </div>
          </div>

          <div class="player-block col-lg-4 col-md-6 col-sm-12 wow fadeInLeft" data-wow-delay="600ms">
            <div class="inner-box hvr-bob">
              <div class="image">
                <a href="#" @click.prevent><img src="/assets/legacy/images/resource/player-9.jpg" alt="" /></a>
              </div>
              <div class="lower-content">
                <h3><a href="#" @click.prevent>Thunderman</a></h3>
                <div class="level">102 level completed</div>
                <ul class="social-icons">
                  <li><a href="#" @click.prevent><span class="fab fa-twitter"></span></a></li>
                  <li><a href="#" @click.prevent><span class="fab fa-facebook-square"></span></a></li>
                  <li><a href="#" @click.prevent><span class="fab fa-pinterest-p"></span></a></li>
                  <li><a href="#" @click.prevent><span class="fab fa-instagram"></span></a></li>
                </ul>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
    <!-- End Players Section -->
  </LegacyLayout>
</template>
