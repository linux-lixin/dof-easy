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
            <li>Faqs</li>
          </ul>
          <h1>Faqs</h1>
        </div>
      </div>
    </section>
    <!--End Page Title-->

    <!-- Faqs Section -->
    <section class="faq-section">
      <div class="auto-container">
        <div class="inner-container clearfix">
          <div class="column col-lg-6 col-md-12 col-sm-12">
            <div class="question-block-two">
              <div class="inner-box">
                <div class="icon-box"><span class="icon flaticon-question"></span></div>
                <h3><a href="#" @click.prevent>How to download the game?</a></h3>
                <div class="text">There are many variations of passages of Lorem Ipsum available, but the majority have suffered alteration in some form, by injected humour.</div>
              </div>
            </div>

            <div class="question-block-two">
              <div class="inner-box">
                <div class="icon-box"><span class="icon flaticon-question"></span></div>
                <h3><a href="#" @click.prevent>how to become a team member?</a></h3>
                <div class="text">There are many variations of passages of Lorem Ipsum available, but the majority have suffered alteration in some form, by injected humour.</div>
              </div>
            </div>

            <div class="question-block-two">
              <div class="inner-box">
                <div class="icon-box"><span class="icon flaticon-question"></span></div>
                <h3><a href="#" @click.prevent>what is the match process?</a></h3>
                <div class="text">There are many variations of passages of Lorem Ipsum available, but the majority have suffered alteration in some form, by injected humour.</div>
              </div>
            </div>

            <div class="question-block-two">
              <div class="inner-box">
                <div class="icon-box"><span class="icon flaticon-question"></span></div>
                <h3><a href="#" @click.prevent>how many players can join a match?</a></h3>
                <div class="text">There are many variations of passages of Lorem Ipsum available, but the majority have suffered alteration in some form, by injected humour.</div>
              </div>
            </div>
          </div>

          <div class="column col-lg-6 col-md-12 col-sm-12">
            <div class="question-block-two alternate">
              <div class="inner-box">
                <div class="icon-box"><span class="icon flaticon-question"></span></div>
                <h3><a href="#" @click.prevent>Is there any age restrictions?</a></h3>
                <div class="text">There are many variations of passages of Lorem Ipsum available, but the majority have suffered alteration in some form, by injected humour.</div>
              </div>
            </div>

            <div class="question-block-two alternate">
              <div class="inner-box">
                <div class="icon-box"><span class="icon flaticon-question"></span></div>
                <h3><a href="#" @click.prevent>Is there any reward for winners?</a></h3>
                <div class="text">There are many variations of passages of Lorem Ipsum available, but the majority have suffered alteration in some form, by injected humour.</div>
              </div>
            </div>

            <div class="question-block-two alternate">
              <div class="inner-box">
                <div class="icon-box"><span class="icon flaticon-question"></span></div>
                <h3><a href="#" @click.prevent>is there any match fee?</a></h3>
                <div class="text">There are many variations of passages of Lorem Ipsum available, but the majority have suffered alteration in some form, by injected humour.</div>
              </div>
            </div>

            <div class="question-block-two alternate">
              <div class="inner-box">
                <div class="icon-box"><span class="icon flaticon-question"></span></div>
                <h3><a href="#" @click.prevent>how to play games?</a></h3>
                <div class="text">There are many variations of passages of Lorem Ipsum available, but the majority have suffered alteration in some form, by injected humour.</div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
    <!-- End Faqs Section -->
  </LegacyLayout>
</template>
