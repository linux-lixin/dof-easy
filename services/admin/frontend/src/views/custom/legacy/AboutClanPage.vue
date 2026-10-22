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
            <li>Matches</li>
          </ul>
          <h1>About Clan</h1>
        </div>
      </div>
    </section>
    <!--End Page Title-->

    <!-- About Section -->
    <section class="about-section">
      <div class="auto-container">
        <!-- Images Gallery -->
        <div class="images-gallery">
          <div class="row clearfix">
            <div class="column col-lg-4 col-md-6 col-sm-12">
              <div class="image wow fadeInLeft" data-wow-delay="0ms">
                <img src="/assets/legacy/images/resource/about-1.jpg" alt="" />
              </div>
            </div>
            <div class="column col-lg-3 col-md-6 col-sm-12">
              <div class="image wow fadeInLeft" data-wow-delay="300ms">
                <img src="/assets/legacy/images/resource/about-2.jpg" alt="" />
              </div>
            </div>
            <div class="column col-lg-5 col-md-6 col-sm-12">
              <div class="image wow fadeInLeft" data-wow-delay="600ms">
                <img src="/assets/legacy/images/resource/about-3.jpg" alt="" />
              </div>
            </div>
          </div>
        </div>

        <!-- Lower Content -->
        <div class="lower-content">
          <div class="row clearfix">
            <div class="column col-lg-4 col-md-12 col-sm-12">
              <h3>A Clan That Slays Together Stays Together</h3>
            </div>

            <div class="column col-lg-4 col-md-12 col-sm-12">
              <p>
                Tincidunt elit magnis nulla facilisis sagittis is maecenas. Sapien nunced amet ultrices, dolores sit
                lorem ipsum is simply free text available in the market velit purus aliquet massa fringilla leo orci.
              </p>
            </div>

            <div class="column col-lg-4 col-md-12 col-sm-12">
              <p>
                Tincidunt elit magnis nulla facilisis sagittis is maecenas. Sapien nunced amet ultrices, dolores sit
                lorem ipsum is simply free text available in the market velit purus aliquet massa fringilla leo orci.
              </p>
            </div>
          </div>
        </div>
      </div>
    </section>
    <!-- End About Section -->

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

    <!-- Call To Action Section -->
    <section class="facts-section">
      <!-- Title Boxed -->
      <div class="title-boxed" style="background-image:url(/assets/legacy/images/background/5.jpg)">
        <div class="auto-container">
          <div class="content wow fadeInLeft" data-wow-delay="0ms">
            <h2>
              Gamon WHERE DIGITAL <br />
              WORLDS MEET REAL <br />
              INNOVATION
            </h2>
            <a href="/contact" class="theme-btn btn-style-one"><span class="btn-title">view tournaments</span></a>
          </div>
        </div>
      </div>
      <!-- End Title Boxed -->

      <!-- Lower Boxed -->
      <div class="lower-boxed">
        <div class="auto-container">
          <div class="row clearfix">
            <!-- Counter Column -->
            <div class="counter-column col-lg-6 col-md-12 col-sm-12">
              <div class="inner-column">
                <div class="featured-block">
                  <div class="inner-box">
                    <div class="icon-box"><span class="icon flaticon-joystick"></span></div>
                    <h3>785,000</h3>
                    <div class="text">Games playerd by 6,388 people in 7 days</div>
                  </div>
                </div>

                <div class="featured-block">
                  <div class="inner-box">
                    <div class="icon-box"><span class="icon flaticon-man"></span></div>
                    <h3>63,000</h3>
                    <div class="text">Players are available to hit you live</div>
                  </div>
                </div>
              </div>
            </div>

            <!-- Testimonial Column -->
            <div class="testimonial-column col-lg-6 col-md-12 col-sm-12">
              <div class="inner-column wow fadeInRight" data-wow-delay="0ms">
                <div class="single-item-carousel owl-carousel owl-theme">
                  <div class="testimonial-block">
                    <div class="inner-box">
                      <div class="quote-icon flaticon-quote-1"></div>
                      <div class="text">
                        Working with Gamon by bytes speed has been awesome. They care about the customer and add the
                        personal feel to customer service. Our esports program has taken a huge step forward working
                        with their help.
                      </div>
                      <div class="author">- Mike Johnson, Jr</div>
                    </div>
                  </div>

                  <div class="testimonial-block">
                    <div class="inner-box">
                      <div class="quote-icon flaticon-quote-1"></div>
                      <div class="text">
                        Working with Gamon by bytes speed has been awesome. They care about the customer and add the
                        personal feel to customer service. Our esports program has taken a huge step forward working
                        with their help.
                      </div>
                      <div class="author">- Mike Johnson, Jr</div>
                    </div>
                  </div>

                  <div class="testimonial-block">
                    <div class="inner-box">
                      <div class="quote-icon flaticon-quote-1"></div>
                      <div class="text">
                        Working with Gamon by bytes speed has been awesome. They care about the customer and add the
                        personal feel to customer service. Our esports program has taken a huge step forward working
                        with their help.
                      </div>
                      <div class="author">- Mike Johnson, Jr</div>
                    </div>
                  </div>
                </div>
              </div>
            </div>

          </div>
        </div>
      </div>
      <!-- End Lower Boxed -->
    </section>
    <!-- End Call To Action Section -->

    <!-- Players Section -->
    <section class="players-section style-two">
      <div class="auto-container">
        <div class="sec-title centered">
          <div class="title">Best Gamers</div>
          <h2>Our players</h2>
        </div>

        <div class="row clearfix">
          <div class="player-block col-lg-4 col-md-6 col-sm-12 wow fadeInLeft" data-wow-delay="0mss">
            <div class="inner-box hvr-bob">
              <div class="image">
                <a href="#" @click.prevent><img src="/assets/legacy/images/resource/player-1.jpg" alt="" /></a>
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
                <a href="#" @click.prevent><img src="/assets/legacy/images/resource/player-2.jpg" alt="" /></a>
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
                <a href="#" @click.prevent><img src="/assets/legacy/images/resource/player-3.jpg" alt="" /></a>
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
        </div>
      </div>
    </section>
    <!-- End Players Section -->
  </LegacyLayout>
</template>
