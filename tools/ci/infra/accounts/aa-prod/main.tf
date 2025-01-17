module "app-system-ci" {
  source = "../.."
  tags = {
    Environment = "Production"
    Owner       = "Zuhlke"
    Application = "TestTraceAppCI"
    Criticality = "Tier 1"
    Revision    = var.ci-infra-revision
  }
  service_role                 = "arn:aws:iam::353189165293:role/ApplicationDeploymentCodeBuild"
  build_failure_events_sns_arn = data.terraform_remote_state.core_infra.outputs.cicd_build_events_sns_arn
  deploy_events_sns_arn        = data.terraform_remote_state.core_infra.outputs.cicd_deploy_events_sns_arn
  account                      = "aa-prod"
  # Secrets manager entry containing the GitHub API token
  github_credentials   = "/ci/github"
  target_environments  = var.target_environments
  allow_dev_pipelines  = false
  allow_prod_pipelines = true
  repository_url       = "https://github.com/nihp-public/covid19-app-system-public.git"
}
