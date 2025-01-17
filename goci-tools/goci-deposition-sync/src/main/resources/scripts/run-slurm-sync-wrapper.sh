#!/bin/bash
#SBATCH -t 04:00:00
#SBATCH --mem=4G
#SBATCH --output=/hps/nobackup/parkinso/spot/gwas/logs/sbatch/slurm-%j.out
#SBATCH --error=/hps/nobackup/parkinso/spot/gwas/logs/sbatch/slurm-%j.err
mode=${1}
echo "mode is ${mode}"
if [ -z "$1" ]
then
  /hps/software/users/parkinso/spot/gwas/prod/sw/deposition_sync/sync_wrapper.sh
else
  /hps/software/users/parkinso/spot/gwas/prod/sw/deposition_sync/sync_wrapper.sh ${mode}
fi
exit $?