{ pkgs, ... }: {
  channel = "unstable";
  packages = [
    pkgs.jdk21
  ];
}
