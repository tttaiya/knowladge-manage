<template>
  <div class="power-background" aria-hidden="true">
    <div class="power-background__gradient power-background__gradient--primary"></div>
    <div class="power-background__gradient power-background__gradient--secondary"></div>
    <div class="power-background__grid"></div>
    <div class="power-background__particles">
      <span
        v-for="particle in particles"
        :key="particle.id"
        class="power-background__particle"
        :style="particle.style"
      />
    </div>
  </div>
</template>

<script setup>
const particles = [
  { id: 1, style: { '--delay': '0s', '--duration': '8s', '--x': '12%', '--size': '6px' } },
  { id: 2, style: { '--delay': '1.2s', '--duration': '10s', '--x': '24%', '--size': '4px' } },
  { id: 3, style: { '--delay': '2.4s', '--duration': '9s', '--x': '37%', '--size': '5px' } },
  { id: 4, style: { '--delay': '0.6s', '--duration': '11s', '--x': '48%', '--size': '3px' } },
  { id: 5, style: { '--delay': '3s', '--duration': '12s', '--x': '59%', '--size': '6px' } },
  { id: 6, style: { '--delay': '1.8s', '--duration': '9.5s', '--x': '68%', '--size': '4px' } },
  { id: 7, style: { '--delay': '2.8s', '--duration': '13s', '--x': '76%', '--size': '5px' } },
  { id: 8, style: { '--delay': '0.9s', '--duration': '10.5s', '--x': '86%', '--size': '3px' } },
  { id: 9, style: { '--delay': '4s', '--duration': '14s', '--x': '92%', '--size': '7px' } },
];
</script>

<style scoped>
.power-background {
  position: fixed;
  inset: 0;
  overflow: hidden;
  pointer-events: none;
  background:
    radial-gradient(circle at 20% 20%, rgba(55, 242, 177, 0.12), transparent 28%),
    radial-gradient(circle at 80% 18%, rgba(61, 139, 255, 0.14), transparent 30%),
    linear-gradient(180deg, var(--pt-page-bg-soft), var(--pt-page-bg));
  z-index: 0;
}

.power-background__gradient {
  position: absolute;
  border-radius: 50%;
  filter: blur(24px);
  opacity: 0.6;
  animation: power-background-float 14s ease-in-out infinite;
}

.power-background__gradient--primary {
  top: -10%;
  left: -8%;
  width: 36vw;
  height: 36vw;
  max-width: 520px;
  max-height: 520px;
  background: radial-gradient(circle, rgba(55, 242, 177, 0.22), transparent 68%);
}

.power-background__gradient--secondary {
  right: -6%;
  bottom: -12%;
  width: 42vw;
  height: 42vw;
  max-width: 640px;
  max-height: 640px;
  background: radial-gradient(circle, rgba(61, 139, 255, 0.22), transparent 68%);
  animation-delay: -7s;
}

.power-background__grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(var(--pt-grid-color) 1px, transparent 1px),
    linear-gradient(90deg, var(--pt-grid-color) 1px, transparent 1px);
  background-size: 72px 72px;
  mask-image: linear-gradient(180deg, rgba(0, 0, 0, 0.08), rgba(0, 0, 0, 0.72));
  opacity: 0.8;
}

.power-background__particles {
  position: absolute;
  inset: 0;
}

.power-background__particle {
  position: absolute;
  top: -12px;
  left: var(--x);
  width: var(--size);
  height: var(--size);
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.75);
  box-shadow: 0 0 16px rgba(55, 242, 177, 0.55);
  animation: power-background-rise var(--duration) linear infinite;
  animation-delay: var(--delay);
}

@keyframes power-background-float {
  0%,
  100% {
    transform: translate3d(0, 0, 0) scale(1);
  }
  50% {
    transform: translate3d(24px, 18px, 0) scale(1.08);
  }
}

@keyframes power-background-rise {
  0% {
    transform: translate3d(0, 0, 0) scale(0.9);
    opacity: 0;
  }
  10% {
    opacity: 0.9;
  }
  100% {
    transform: translate3d(0, calc(100vh + 24px), 0) scale(1.15);
    opacity: 0;
  }
}
</style>

