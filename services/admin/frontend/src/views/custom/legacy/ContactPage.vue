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
            <li>Contact</li>
          </ul>
          <h1>Contact Us</h1>
        </div>
      </div>
    </section>
    <!--End Page Title-->

    <!-- Contact Form Section -->
    <section class="contact-form-section style-two">
      <div class="auto-container">
        <div class="row clearfix">
          <div class="title-column col-lg-5 col-md-12 col-sm-12">
            <div class="inner-column wow fadeInLeft" data-wow-delay="0ms">
              <div class="sec-title">
                <div class="title">Contact With Us</div>
                <h2>we’re here to help you</h2>
              </div>
              <div class="text">
                There are many variations of passages of lorem ipsum available, but the majority have suffered
                alteration in some form injected humour, or simply free textinh randomised words which don't look even
                slightly believable.
              </div>
              <ul class="social-icons">
                <li><a href="#" @click.prevent><span class="fab fa-twitter"></span></a></li>
                <li><a href="#" @click.prevent><span class="fab fa-facebook-square"></span></a></li>
                <li><a href="#" @click.prevent><span class="fab fa-pinterest-p"></span></a></li>
                <li><a href="#" @click.prevent><span class="fab fa-instagram"></span></a></li>
              </ul>
            </div>
          </div>

          <div class="form-column col-lg-7 col-md-12 col-sm-12">
            <div class="inner-column wow fadeInRight" data-wow-delay="0ms">
              <div class="default-form contact-form">
                <form method="post" action="sendemail.php" id="contact-form" @submit.prevent>
                  <div class="row clearfix">
                    <div class="col-md-6 col-sm-12 form-group">
                      <input type="text" name="username" placeholder="Full name" required />
                    </div>
                    <div class="col-md-6 col-sm-12 form-group">
                      <input type="email" name="email" placeholder="Email address" required />
                    </div>
                    <div class="col-md-12 col-sm-12 form-group">
                      <textarea name="message" placeholder="Write a Message"></textarea>
                    </div>
                    <div class="col-md-12 col-sm-12 form-group">
                      <button class="theme-btn btn-style-one" type="submit" name="submit-form">
                        <span class="btn-title">Send Message</span>
                      </button>
                    </div>
                  </div>
                </form>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
    <!-- End Contact Form Section -->

    <!--Map Section-->
    <section class="map-section">
      <div class="auto-container">
        <div class="map-outer">
          <div
            class="map-canvas"
            data-zoom="12"
            data-lat="-37.817085"
            data-lng="144.955631"
            data-type="roadmap"
            data-hue="#ffc400"
            data-title="Envato"
            data-icon-path="/assets/legacy/images/icons/map-marker.png"
            data-content="Melbourne VIC 3000, Australia<br><a href='mailto:info@youremail.com'>info@youremail.com</a>"
          ></div>
        </div>
      </div>
    </section>
    <!--End Map Section-->
  </LegacyLayout>
</template>
