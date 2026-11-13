<!-- An ID card sliding into a card reader, drawn with SVG and CSS only -->
<template>
  <svg
    class="id-card-graphic"
    viewBox="0 0 240 190"
    role="img"
    aria-label="แอนิเมชันแสดงการเสียบบัตรประชาชนที่เครื่องอ่านบัตร"
    xmlns="http://www.w3.org/2000/svg"
  >
    <g class="card-moving">
      <rect x="62" y="14" width="116" height="80" rx="9" fill="#e8f2fb" stroke="#7aa6cf" stroke-width="2" />
      <rect x="72" y="30" width="28" height="34" rx="3" fill="#b9d3ea" />
      <rect x="108" y="32" width="58" height="6" rx="3" fill="#9dbfdc" />
      <rect x="108" y="46" width="46" height="6" rx="3" fill="#9dbfdc" />
      <rect x="108" y="60" width="52" height="6" rx="3" fill="#9dbfdc" />
      <rect x="72" y="72" width="20" height="14" rx="3" fill="#e0b84a" />
    </g>
    <g class="reader-body">
      <rect x="30" y="110" width="180" height="64" rx="12" fill="#2f3b39" />
      <rect x="48" y="118" width="144" height="8" rx="4" fill="#0f1514" />
      <circle class="reader-light" cx="198" cy="152" r="4" fill="#3fd17a" />
    </g>
  </svg>
</template>

<style scoped>
.id-card-graphic {
  width: 220px;
  max-width: 100%;
  height: auto;
}
.card-moving {
  animation: card-insert 2.6s ease-in-out infinite;
}
.reader-light {
  animation: reader-blink 1.3s ease-in-out infinite;
}
@keyframes card-insert {
  0% {
    transform: translateY(-6px);
  }
  55%,
  75% {
    transform: translateY(54px);
  }
  100% {
    transform: translateY(-6px);
  }
}
@keyframes reader-blink {
  50% {
    opacity: 0.25;
  }
}
@media (prefers-reduced-motion: reduce) {
  .card-moving,
  .reader-light {
    animation: none;
  }
}
</style>
