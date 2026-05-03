#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <string.h>
#include <fcntl.h>
#include <signal.h>

#define MAX_FREQS 32

static int freqs[MAX_FREQS];
static int freq_count = 0;
static int current_freq = -1;
static int fd_stat = -1;
static int fd_policy = -1;

void handle_sigint(int sig) {
    printf("\n[!] Exit: Big Cluster Scaler stopped.\n");
    if (fd_stat >= 0) close(fd_stat);
    if (fd_policy >= 0) close(fd_policy);
    exit(0);
}

void load_freqs() {
    FILE *f = fopen("/proc/ppm/dump_cluster_0_dvfs_table", "r");
    if (!f) return;
    while (freq_count < MAX_FREQS && fscanf(f, "%d", &freqs[freq_count]) == 1) {
        freq_count++;
    }
    fclose(f);
}

int get_cpu_usage() {
    static long prev_total = 0, prev_idle = 0;
    static char buf[128]; 
    if (fd_stat < 0) return 0;

    lseek(fd_stat, 0, SEEK_SET);
    ssize_t n = read(fd_stat, buf, sizeof(buf) - 1);
    if (n <= 0) return 0;
    buf[n] = '\0';

    long user, nice, sys, idle, iowait, irq, softirq;
    if (sscanf(buf + 4, "%ld %ld %ld %ld %ld %ld %ld",
               &user, &nice, &sys, &idle, &iowait, &irq, &softirq) != 7) return 0;

    long total = user + nice + sys + idle + iowait + irq + softirq;
    long dt = total - prev_total;
    long di = idle - prev_idle;

    if (dt == 0) return 0;
    prev_total = total; prev_idle = idle;
    return (int)((100 * (dt - di)) / dt);
}

void set_big_freq(int usage) {
    if (fd_policy < 0 || freq_count == 0) return;

    int target_idx;
    // Linear Scaling Logic: usage 15% to 85% maps to the 16 steps
    if (usage < 25) {
        target_idx = freq_count - 1; // 400MHz
    } else if (usage > 90) {
        target_idx = 0;              // 2.1GHz
    } else {
        // Map usage to the index table
        target_idx = (freq_count - 1) - ((usage - 15) * (freq_count - 1) / 70);
    }

    // Safety check
    if (target_idx < 0) target_idx = 0;
    if (target_idx >= freq_count) target_idx = freq_count - 1;

    int freq = freqs[target_idx];

    if (freq == current_freq) return;

    static char out_buf[16];
    int len = snprintf(out_buf, sizeof(out_buf), "0 %d", freq);
    lseek(fd_policy, 0, SEEK_SET);
    if (write(fd_policy, out_buf, len) > 0) {
        current_freq = freq;
    }
}

int main() {
    signal(SIGINT, handle_sigint);
    load_freqs();
    fd_stat = open("/proc/stat", O_RDONLY);
    fd_policy = open("/proc/ppm/policy/userlimit_max_cpu_freq", O_WRONLY);

    while (1) {
        set_big_freq(get_cpu_usage());
        usleep(250000); 
    }
    return 0;
}

